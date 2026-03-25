package phiner.de5.net.gateway.strategy;

import com.dukascopy.api.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import phiner.de5.net.gateway.config.ForexProperties;
import phiner.de5.net.gateway.KLineManager;
import phiner.de5.net.gateway.TickManager;
import phiner.de5.net.gateway.dto.*;
import phiner.de5.net.gateway.request.*;
import phiner.de5.net.gateway.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@Component
public class TradingStrategy implements IStrategy {

  private IContext context;
  private ExecutorService executor; // 原始执行器，可能用于订单相关操作
  private ExecutorService eventProcessor; // 用于异步处理 JForex 事件的新执行器
  private ScheduledExecutorService syncScheduler; // 用于持仓同步的延时执行器
  private ScheduledFuture<?> syncFuture; // 维护当前的延时任务
  private ScheduledFuture<?> historySyncFuture; // 维护历史同步的延时任务
  private final AtomicBoolean syncPending = new AtomicBoolean(false); // 标记是否有待处理的同步

  private final Set<Instrument> subscribedInstruments = ConcurrentHashMap.newKeySet();
  private final Map<String, Double> pendingTakeProfits = new ConcurrentHashMap<>();
  private final Set<Instrument> pendingHistorySyncs = ConcurrentHashMap.newKeySet();
  private final Map<String, long[]> historyCoverageCache = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong historySyncStartTime = new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
  private final java.util.concurrent.atomic.AtomicLong historySyncEndTime = new java.util.concurrent.atomic.AtomicLong(0);
  private final Set<Period> configuredPeriods = new HashSet<>();
  private final TickManager tickManager;
  private final KLineManager kLineManager;
  private final RedisService redisService;
  private final ForexProperties forexProperties;

  @Value("${gateway.kline.storage-limit}")
  private int klineStorageLimit;

  @Value("${gateway.heartbeat.fixed-rate:15000}")
  private long heartbeatRate;

  public TradingStrategy(
      TickManager tickManager, KLineManager kLineManager, RedisService redisService, ForexProperties forexProperties) {
    this.tickManager = tickManager;
    this.kLineManager = kLineManager;
    this.redisService = redisService;
    this.forexProperties = forexProperties;
  }

  @Override
  public void onStart(IContext context) {
    log.info("TradingStrategy onStart entered. Context: {}", context != null ? "not null" : "null");
    if (forexProperties != null) {
      log.info("Configured instruments: {}", forexProperties.getInstruments());
      log.info("Configured periods: {}", forexProperties.getPeriods());
    } else {
      log.error("forexProperties is null!");
    }
    this.context = context;
    if (this.executor == null) {
        this.executor = Executors.newSingleThreadExecutor();
    }
    // 初始化事件处理器，用于异步处理报价、K线和消息
    this.eventProcessor = Executors.newSingleThreadExecutor();
    // 初始化防抖同步调度器
    if (this.syncScheduler == null) {
        this.syncScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    if (context != null) {
      // 从配置中订阅交易产品
      if (forexProperties.getInstruments() != null && !forexProperties.getInstruments().isEmpty()) {
        Set<Instrument> instrumentsToSubscribe = forexProperties.getInstruments().stream()
            .map(String::trim)
            .map(name -> {
              try {
                Instrument inst = parseInstrument(name);
                if (inst != null) {
                  log.info("已将字符串 '{}' 转换为产品: {}", name, inst);
                } else {
                  log.error("无法将 '{}' 转换为有效的产品", name);
                  redisService.publishError("配置中的交易产品名称无效: " + name);
                }
                return inst;
              } catch (Exception e) {
                log.error("解析产品 '{}' 时发生异常", name, e);
                redisService.publishError("配置中的交易产品名称无效: " + name);
                return null;
              }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!instrumentsToSubscribe.isEmpty()) {
          this.subscribedInstruments.addAll(instrumentsToSubscribe);
          log.info("正在订阅产品: {}", this.subscribedInstruments);
          context.setSubscribedInstruments(this.subscribedInstruments, true);
          String subscribed = instrumentsToSubscribe.stream()
              .map(Instrument::name)
              .collect(Collectors.joining(", "));
          log.info("成功订阅产品: {}", subscribed);
          redisService.publishInfo("成功订阅产品: " + subscribed);
          
          // 将详细的产品信息保存到 Redis 静态键
          for (Instrument instrument : this.subscribedInstruments) {
              saveInstrumentDetailsToRedis(instrument);
          }
        } else {
          log.warn("没有有效的产品可供订阅！");
        }
      }

      // 解析并存储配置的周期
      if (forexProperties.getPeriods() != null && !forexProperties.getPeriods().isEmpty()) {
        Set<Period> periodsToProcess = forexProperties.getPeriods().stream()
            .map(String::trim)
            .map(name -> {
                try {
                    return Period.valueOf(name);
                } catch (IllegalArgumentException e) {
                    redisService.publishError("配置中的周期名称无效: " + name);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        this.configuredPeriods.addAll(periodsToProcess);
        String periods = periodsToProcess.stream().map(Period::toString).collect(Collectors.joining(", "));
        log.info("将处理以下周期的 K线: {}", periods);
        redisService.publishInfo("将处理以下周期的 K线: " + periods);

        // 异步预加载历史数据，避免阻塞 JForex 启动线程
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
              log.info("异步历史预加载器: 正在等待服务器确认产品订阅...");
              long startWait = System.currentTimeMillis();
              // 轮询直到服务器报告所有请求的产品均已订阅
              while (!context.getSubscribedInstruments().containsAll(this.subscribedInstruments)) {
                  if (System.currentTimeMillis() - startWait > 30000) {
                      log.warn("异步历史预加载器: 等待订阅确认超时 (30s)。将继续处理已完成订阅的产品。");
                      break;
                  }
                  try {
                      Thread.sleep(1000);
                  } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                  }
              }
              log.info("异步历史预加载器: 订阅已在 {}ms 内确认。开始获取历史数据。", System.currentTimeMillis() - startWait);
              IHistory history = context.getHistory();
              log.info("异步历史预加载器: klineStorageLimit: {}, contextTime: {}", klineStorageLimit, context.getTime());
              for (Instrument instrument : this.subscribedInstruments) {
                String instrumentName = instrument.toString();
                for (Period period : this.configuredPeriods) {
                  try {
                    log.info("异步历史预加载器: 正在请求 {} 的 {} 周期历史数据", instrumentName, period);
                    // 计算范围：从最后一个已连续的 K线开始执行
                    long to = history.getPreviousBarStart(period, context.getTime());
                    long from = to - (klineStorageLimit * period.getInterval());
                    
                    // 获取历史 K线数据
                    List<IBar> bars = history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, from, to);
                    
                    if (bars != null && !bars.isEmpty()) {
                        log.info("异步历史预加载器: 收到 {} 的 {} 周期共 {} 条历史记录", instrumentName, period, bars.size());
                        if (instrumentName != null) {
                            for (IBar bar : bars) {
                                BarDTO barDTO = new BarDTO(instrumentName, period.toString(), bar);
                                // 使用顺序事件处理器处理 K线
                                eventProcessor.submit(() -> kLineManager.onBar(instrumentName, barDTO));
                            }
                        }
                    } else if (bars != null) {
                        log.warn("异步历史预加载器: 在 {} 到 {} 范围内未收到 {} 的 {} 周期数据", from, to, instrumentName, period);
                    } else {
                        log.warn("异步历史预加载器: 收到 {} 的 {} 周期数据为 null", instrumentName, period);
                    }
                  } catch (Exception e) {
                    log.error("异步历史预加载器: 无法预加载 {} 的 {} 周期历史数据", instrumentName, period, e);
                  }
                }
              }
            } catch (Exception e) {
              log.error("异步历史预加载器遇到通用错误", e);
            }
        }, this.executor);
      }

      redisService.publishGatewayStatus(
          new GatewayStatusDTO(
              "CONNECTED", "交易策略已启动并成功连接至 Dukascopy。"));
      redisService.publishInfo("交易策略已启动。");

      // Startup sync
      runTask(this::executeFullPositionSync, "Startup Full Position Sync");
    }
  }

  @Override
  public void onTick(Instrument instrument, ITick tick) {
    if (instrument != null && tick != null && subscribedInstruments.contains(instrument)) {
      String instrumentName = instrument.toString();
      if (instrumentName != null && eventProcessor != null && !eventProcessor.isShutdown()) {
        eventProcessor.submit(() -> tickManager.onTick(instrumentName, tick));
      }
    }
  }

  @Override
  public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
    if (instrument != null
        && period != null
        && bidBar != null
        && subscribedInstruments.contains(instrument)
        && configuredPeriods.contains(period)) {
      String instrumentName = instrument.toString();
      String periodName = period.toString();
      
      if (instrumentName != null && periodName != null && eventProcessor != null && !eventProcessor.isShutdown()) {
        eventProcessor.submit(() -> {
            BarDTO barDTO = new BarDTO(instrumentName, periodName, bidBar);
            kLineManager.onBar(instrumentName, barDTO);
        });
      }
    }
  }

  @Override
  public void onMessage(IMessage message) {
    if (message != null && eventProcessor != null && !eventProcessor.isShutdown()) {
      eventProcessor.submit(() -> {
          redisService.publishOrderEvent(message);
          try {
            IOrder order = message.getOrder();
            IMessage.Type type = message.getType();
            if (order != null && (type == IMessage.Type.ORDER_FILL_OK || 
                                  type == IMessage.Type.ORDER_CLOSE_OK || 
                                  type == IMessage.Type.ORDER_CHANGED_OK)) {
              boolean skipSync = false;
              // 异步逻辑：如果 SL 修改成功且存在待处理的 TP 修改，则通过策略线程触发它
               if (type == IMessage.Type.ORDER_CHANGED_OK) {
                   Double targetTP = pendingTakeProfits.remove(order.getId());
                   if (targetTP != null) {
                       log.info("SL modification confirmed for {}, now applying pending TP: {}", order.getLabel(), targetTP);
                       // 必须通过 context.executeTask 将 TP 修改提交到 JForex 策略线程执行，
                       // 直接在 eventProcessor 线程中调用 order.setTakeProfitPrice 会抛出 "Incorrect thread" 错误。
                       context.executeTask(() -> {
                           try {
                               order.setTakeProfitPrice(targetTP);
                               log.info("Successfully applied pending TP {} for order {}", targetTP, order.getLabel());
                           } catch (Exception e) {
                               log.error("Failed to apply pending TP for order {}: {}", order.getLabel(), e.getMessage());
                           }
                           return null;
                       });
                       skipSync = true; // 状态感知：TP 修改的 ORDER_CHANGED_OK 事件到来时会再次触发同步
                   }
               }

              if (!skipSync) {
                  log.info("Order event {} for {}, scheduling debounced position sync", type, order.getLabel());
                  scheduleDebouncedPositionSync();
              }
            }
          } catch (Exception e) {
            log.error("Error processing order message for position sync", e);
          }
      });
    }
  }

  @Override
  public void onAccount(IAccount account) {
    if (account != null && eventProcessor != null && !eventProcessor.isShutdown()) {
      eventProcessor.submit(() -> {
          double balance = account.getBalance();
          double equity = account.getEquity();
          double baseEquity = account.getBaseEquity();
          double margin = account.getUsedMargin();
          double unrealizedPL = equity - baseEquity;
          
          redisService.publishAccountStatus(balance, equity, baseEquity, margin, unrealizedPL);
      });
    }
  }

  @Override
  public void onStop() {
    log.info("TradingStrategy stopping. Shutting down executors...");
    if (executor != null) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    if (eventProcessor != null) {
        eventProcessor.shutdown();
        try {
            if (!eventProcessor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                eventProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    if (syncScheduler != null) {
        syncScheduler.shutdownNow();
    }
    
    try {
        redisService.publishGatewayStatus(
            new GatewayStatusDTO("DISCONNECTED", "Trading strategy stopped."));
        redisService.publishInfo("Trading strategy stopped.");
    } catch (Exception e) {
        log.warn("Could not publish final disconnect status to Redis: {}", e.getMessage());
    }
    log.info("TradingStrategy stopped.");
  }

  public IContext getContext() {
    return context;
  }

  /**
   * 定期发送心跳和账户状态，解决前端显示 DISCONNECTED 的问题并保持数据同步。
   */
  @Scheduled(fixedRateString = "${gateway.heartbeat.fixed-rate:15000}")
  public void heartbeat() {
    if (context != null) {
      // 广播网关连接状态
      redisService.publishGatewayStatus(
          new GatewayStatusDTO("CONNECTED", "Heartbeat: Gateway is active and processing events."));
      
      // 广播账户最新状态
      try {
          IAccount account = context.getAccount();
          if (account != null) {
              onAccount(account);
          }
      } catch (Exception e) {
          log.warn("Failed to fetch account info during heartbeat", e);
      }
    }
  }

  public void setExecutor(ExecutorService executor) {
      this.executor = executor;
  }

  public void setSyncScheduler(ScheduledExecutorService syncScheduler) {
      this.syncScheduler = syncScheduler;
  }

  public void executeMarketOrder(OpenMarketOrderRequest request) {
        runTask(() -> {
        Instrument instrument = parseInstrument(request.getInstrument());
        if (instrument == null) {
            log.error("Invalid instrument in market order request: {}", request.getInstrument());
            redisService.publishError("Invalid instrument: " + request.getInstrument());
            return null;
        }
        IEngine.OrderCommand command = (request.getOrderType() == MarketOrderType.BUY) ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL;
        double amount = request.getAmount() != null ? request.getAmount() : 0.0;
        String finalLabel = sanitizeLabel((request.getLabel() != null && !request.getLabel().isEmpty()) ? request.getLabel() : getNewLabel());

        context.getEngine().submitOrder(
                finalLabel,
                instrument,
                command,
                amount,
                0, // 价格
                request.getSlippage() != null ? request.getSlippage() : 0.0, // 滑点
                request.getStopLossPrice() != null ? request.getStopLossPrice() : 0.0, // 止损价
                request.getTakeProfitPrice() != null ? request.getTakeProfitPrice() : 0.0 // 止盈价
        );
            return null;
        }, "Open Market Order [" + request.getInstrument() + "]");
    }

    public void closeMarketOrder(CloseMarketOrderRequest request) {
        runTask(() -> {
        IOrder order = context.getEngine().getOrderById(request.getOrderId());
        if (order != null) {
                order.close();
            } else {
                log.warn("Could not find order to close: {}", request.getOrderId());
            }
            return null;
        }, "Close Market Order [" + request.getOrderId() + "]");
    }

    public void submitOrder(SubmitOrderRequest request) {
        runTask(() -> {
            Instrument instrument = parseInstrument(request.getInstrument());
            if (instrument == null) {
                log.error("Invalid instrument in order request: {}", request.getInstrument());
                redisService.publishError("Invalid instrument: " + request.getInstrument());
                return null;
            }
            IEngine.OrderCommand command = IEngine.OrderCommand.valueOf(request.getOrderCommand());
            String finalLabel = sanitizeLabel((request.getLabel() != null && !request.getLabel().isEmpty()) ? request.getLabel() : getNewLabel());

            double stopLossPrice = request.getStopLossPrice() != null ? request.getStopLossPrice() : 0.0;
            double takeProfitPrice = request.getTakeProfitPrice() != null ? request.getTakeProfitPrice() : 0.0;
            double amount = request.getAmount() != null ? request.getAmount() : 0.0;
            double price = request.getPrice() != null ? request.getPrice() : 0.0;
            double slippage = request.getSlippage() != null ? request.getSlippage() : 0.0;

            context.getEngine().submitOrder(
                    finalLabel,
                    instrument,
                    command,
                    amount,
                    price,
                    slippage,
                    stopLossPrice,
                    takeProfitPrice,
                    null,
                    request.getComments()
            );
            return null;
        }, "Submit Order [" + request.getInstrument() + "]");
    }

    public void modifyOrder(ModifyOrderRequest request) {
        runTask(() -> {
            IOrder order = context.getEngine().getOrderById(request.getOrderId());
            if (order != null) {
                double stopLossPrice = request.getStopLossPrice();
                double takeProfitPrice = request.getTakeProfitPrice();

                if (stopLossPrice > 0) {
                    // 如果同时涉及 TP，先记录 TP，待 SL 修改成功后再触发
                    if (takeProfitPrice > 0) {
                        pendingTakeProfits.put(order.getId(), takeProfitPrice);
                        log.debug("Queued TP modification for order {}: {}", request.getOrderId(), takeProfitPrice);
                    }
                    order.setStopLossPrice(stopLossPrice);
                } else if (takeProfitPrice > 0) {
                    // 仅修改 TP
                    order.setTakeProfitPrice(takeProfitPrice);
                }
            } else {
                log.warn("Could not find order to modify: {}", request.getOrderId());
            }
            return null;
        }, "Modify Order [" + request.getOrderId() + "]");
    }

    public void cancelOrder(CancelOrderRequest request) {
        runTask(() -> {
            IOrder order = context.getEngine().getOrderById(request.getOrderId());
            if (order != null && order.getState() == IOrder.State.OPENED) {
                order.close();
            } else if (order == null) {
                log.warn("Could not find order to cancel: {}", request.getOrderId());
            }
            return null;
        }, "Cancel Order [" + request.getOrderId() + "]");
    }

    public void handleInstrumentInfoRequest(@NonNull InstrumentInfoRequest request) {
        runTask(() -> {
            String instrumentName = request.getInstrument();
            String requestId = request.getRequestId();

            Instrument instrument = parseInstrument(instrumentName);
            if (instrument == null) {
                log.error("Instrument info request failed: instrument not found for {}", instrumentName);
                redisService.publishError("Instrument not found: " + instrumentName);
                return null;
            }

            InstrumentInfoDTO infoDTO = createInstrumentInfoDTO(instrument);
            if (infoDTO == null) {
                redisService.publishError("Error fetching instrument info for " + instrumentName + ": received null values from API.");
                return null;
            }

            redisService.saveInstrumentInfo(infoDTO);
            redisService.publishInstrumentInfo(infoDTO, requestId);
            return null;
        }, "Instrument Info Request [" + request.getInstrument() + "]");
    }

    public void handlePositionsRequest(@NonNull phiner.de5.net.gateway.request.PositionsRequest request) {
        log.info("Manual positions request received, scheduling debounced sync to Hash");
        scheduleDebouncedPositionSync();
    }

    @FunctionalInterface
    private interface ITask<T> {
        T onExecute() throws Exception;
    }

    private void runTask(ITask<Void> task, String errorContext) {
        if (context == null) {
            log.error("JForex Context is not initialized. Failed: {}", errorContext);
            redisService.publishError("JForex Context not initialized. " + errorContext);
            ErrorDTO error = ErrorDTO.validationError("CONTEXT_NOT_INITIALIZED", "JForex Context is not initialized", errorContext);
            redisService.publishStructuredError(error);
            return;
        }
        context.executeTask(() -> {
            try {
                return task.onExecute();
            } catch (Exception e) {
                log.error("Exception in JForex thread: {}", errorContext, e);
                redisService.publishError("JForex Thread Error: " + errorContext + " - " + e.getMessage());
                ErrorDTO structuredError = extractErrorFromException(e, errorContext);
                redisService.publishStructuredError(structuredError);
                return null;
            }
        });
    }

    private ErrorDTO extractErrorFromException(Exception e, String context) {
        String code = "UNKNOWN_ERROR";
        String message = e.getMessage();

        String exceptionMessage = e.getMessage();
        if (exceptionMessage != null) {
            if (exceptionMessage.contains("LABEL_NOT_UNIQUE") || exceptionMessage.contains("not unique")) {
                code = "LABEL_NOT_UNIQUE";
                message = "Order label already exists. Each order label must be unique.";
            } else if (exceptionMessage.contains("LABEL_INCONSISTENT") || exceptionMessage.contains("inconsistent")) {
                code = "LABEL_INCONSISTENT";
                message = "Order label conflict with existing order.";
            } else if (exceptionMessage.contains("INVALID_AMOUNT") || exceptionMessage.contains("Invalid amount")) {
                code = "INVALID_AMOUNT";
                message = "Invalid order amount. Check minimum/maximum trade amount.";
            } else if (exceptionMessage.contains("ORDER_INCORRECT") || exceptionMessage.contains("Incorrect")) {
                code = "ORDER_INCORRECT";
                message = "Order parameters are incorrect.";
            } else if (exceptionMessage.contains("ORDER_STATE_IMMUTABLE") || exceptionMessage.contains("state")) {
                code = "ORDER_STATE_IMMUTABLE";
                message = "Order state cannot be modified in current state.";
            } else if (exceptionMessage.contains("ORDER_CANCEL_INCORRECT")) {
                code = "ORDER_CANCEL_INCORRECT";
                message = "Cannot cancel order in current state.";
            } else if (exceptionMessage.contains("ORDERS_UNAVAILABLE") || exceptionMessage.contains("unavailable")) {
                code = "ORDERS_UNAVAILABLE";
                message = "Trading is currently unavailable.";
            } else if (exceptionMessage.contains("QUEUE_OVERLOADED") || exceptionMessage.contains("queue")) {
                code = "QUEUE_OVERLOADED";
                message = "Order queue is overloaded. Try again later.";
            } else if (exceptionMessage.contains("ZERO_PRICE") || exceptionMessage.contains("zero price")) {
                code = "ZERO_PRICE_NOT_ALLOWED";
                message = "Zero price is not allowed for this order type.";
            } else if (exceptionMessage.contains("INVALID_GTT") || exceptionMessage.contains("GTT")) {
                code = "INVALID_GTT";
                message = "Invalid Good-Till-Time settings.";
            } else if (exceptionMessage.contains("NO_ACCOUNT_SETTINGS")) {
                code = "NO_ACCOUNT_SETTINGS_RECEIVED";
                message = "Account settings not received yet.";
            } else if (exceptionMessage.contains("CALL_INCORRECT")) {
                code = "CALL_INCORRECT";
                message = "API call made from incorrect context/thread.";
            } else if (exceptionMessage.contains("COMMAND_IS_NULL") || exceptionMessage.contains("null")) {
                code = "COMMAND_IS_NULL";
                message = "Order command is null.";
            }
        }

        if (e instanceof IllegalArgumentException) {
            code = "VALIDATION_ERROR";
            message = "Invalid argument: " + e.getMessage();
        } else if (e instanceof NullPointerException) {
            code = "NULL_POINTER";
            message = "Null reference in " + context;
        }

        return new ErrorDTO(code, message, "ORDER_ERROR", context);
    }

    private void saveInstrumentDetailsToRedis(@NonNull Instrument instrument) {
        try {
            InstrumentInfoDTO infoDTO = createInstrumentInfoDTO(instrument);
            if (infoDTO != null) {
                redisService.saveInstrumentInfo(infoDTO);
            } else {
                log.error("Failed to fetch full instrument details for {}", instrument);
            }
        } catch (Exception e) {
            log.error("Error saving instrument info for {} to Redis", instrument, e);
        }
    }

    private InstrumentInfoDTO createInstrumentInfoDTO(Instrument instrument) {
        String name = instrument.toString();
        ICurrency primaryCurrency = instrument.getPrimaryJFCurrency();
        ICurrency secondaryCurrency = instrument.getSecondaryJFCurrency();

        if (name == null || primaryCurrency == null || secondaryCurrency == null) {
            return null;
        }

        String primaryCode = primaryCurrency.getCurrencyCode();
        String secondaryCode = secondaryCurrency.getCurrencyCode();

        if (primaryCode == null || secondaryCode == null) {
            return null;
        }

        String currency = primaryCode + "/" + secondaryCode;
        return new InstrumentInfoDTO(
                name,
                currency,
                java.math.BigDecimal.valueOf(instrument.getPipValue()).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                java.math.BigDecimal.valueOf(Math.pow(10, -instrument.getTickScale())).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                name,
                instrument.getMinTradeAmount()
        );
    }

  private String getNewLabel() {
    return "Order_" + System.currentTimeMillis();
  }

  private String sanitizeLabel(String label) {
      if (label == null || label.isEmpty()) {
          return getNewLabel();
      }
      if (!label.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
          log.error("Label may be invalid according to JForex rules (contains special chars or starts with digit): {}", label);
      }
      return label;
  }

  /**
   * 严格解析 Instrument，不进行隐式转换以符合用户期望。
   */
  private Instrument parseInstrument(String instrumentName) {
      if (instrumentName == null || instrumentName.isEmpty()) {
          return null;
      }
      Instrument instrument = Instrument.fromString(instrumentName);
      if (instrument == null) {
          log.error("Failed to parse instrument strictly from string: {}", instrumentName);
      }
      return instrument;
  }

  public Set<Instrument> getSubscribedInstruments() {
    return subscribedInstruments;
  }

  public void subscribeToInstrument(Instrument instrument) {
    if (context != null && subscribedInstruments.add(instrument)) {
      context.setSubscribedInstruments(subscribedInstruments, true);
    }
  }

  /**
   * 触发防抖的全局持仓同步 (True Debounce)
   * 收纳短时间内如潮水般涌入的事件，延迟执行，重置计时器。
   */
  private void scheduleDebouncedPositionSync() {
      // 每次调用时，如果已有正在倒计时的任务，且未开始执行，则取消它
      if (syncFuture != null && !syncFuture.isDone()) {
          syncFuture.cancel(false);
      }
      
      // 标记有同步任务正在等待
      syncPending.set(true);
      
      // 调度一个新的任务，在 1 秒（1000毫秒）后执行
      syncFuture = syncScheduler.schedule(() -> {
          // 在真正执行前，检查并清除标志。由于这是单线程池，不需要太复杂的锁
          if (syncPending.compareAndSet(true, false)) {
              executeFullPositionSync();
          }
      }, 1000, TimeUnit.MILLISECONDS);
  }

  /**
   * 触发历史同步的防抖 (Debounce for History Sync)
   * 将多个品种的历史提取请求合并为一次顺序批处理。
   */
  private void scheduleDebouncedHistorySync(Instrument instrument, long startTime, long endTime) {
      // 1. 更新缓冲池：增加品种并扩展时间跨度以覆盖所有请求
      pendingHistorySyncs.add(instrument);
      
      long currentMin = historySyncStartTime.get();
      if (startTime < currentMin) {
          historySyncStartTime.compareAndSet(currentMin, startTime);
      }
      
      long currentMax = historySyncEndTime.get();
      if (endTime > currentMax) {
          historySyncEndTime.compareAndSet(currentMax, endTime);
      }

      // 2. 重置防抖计时器
      if (historySyncFuture != null && !historySyncFuture.isDone()) {
          historySyncFuture.cancel(false);
      }

      log.info("Added {} to pending history sync queue, scheduling coalesced sync in 1s", instrument);

      historySyncFuture = syncScheduler.schedule(() -> {
          executeCoalescedHistorySync();
      }, 1000, TimeUnit.MILLISECONDS);
  }

  /**
   * 实际执行合并后的顺序历史同步逻辑
   */
  private void executeCoalescedHistorySync() {
      if (context == null || context.getHistory() == null || context.getEngine() == null) {
          return;
      }

      // 1. 提取快照并重置缓冲区
      Set<Instrument> instrumentsToSync = new HashSet<>(pendingHistorySyncs);
      pendingHistorySyncs.clear();
      
      long from = historySyncStartTime.getAndSet(Long.MAX_VALUE);
      long to = historySyncEndTime.getAndSet(0);

      if (instrumentsToSync.isEmpty()) return;

      log.info("Executing coalesced history sync for {} instruments: {}, from={}, to={}", 
               instrumentsToSync.size(), instrumentsToSync, from, to);

      // 如果 endTime 为 0 或非法，降级为当前时间
      if (to <= 0) to = context.getTime();
      if (from == Long.MAX_VALUE) from = to - (24 * 3600 * 1000); // 默认过去24小时

      final long finalFrom = from;
      final long finalTo = to;

      // 2. 提交到单线程 executor 顺序执行
      executor.submit(() -> {
          try {
              for (Instrument instrument : instrumentsToSync) {
                  log.info("Sequential History Extraction starting for {}", instrument);
                  try {
                      List<IOrder> historyOrders = context.getHistory().getOrdersHistory(instrument, finalFrom, finalTo);
                      if (historyOrders != null && !historyOrders.isEmpty()) {
                          List<OrderHistoryDTO> dtos = new java.util.ArrayList<>();
                          for (IOrder order : historyOrders) {
                              try {
                                  dtos.add(new OrderHistoryDTO(order));
                              } catch (JFException e) {
                                  log.error("Failed to convert order {} to DTO: {}", order.getId(), e.getMessage());
                              }
                          }
                          redisService.updateHistoryHash(dtos);
                          // 成功后更新 Redis 和本地内存缓存
                          long[] newRange = new long[]{finalFrom, finalTo};
                          historyCoverageCache.put(instrument.toString(), newRange);
                          redisService.updateHistoryCoverage(instrument.toString(), finalFrom, finalTo);
                          log.info("Successfully extracted {} historical orders for {}", dtos.size(), instrument);
                      } else {
                          log.info("No historical orders found for {} in target range", instrument);
                          // 即使没有订单，也认为这段时间已经扫描过了，更新内存和 Redis
                          long[] newRange = new long[]{finalFrom, finalTo};
                          historyCoverageCache.put(instrument.toString(), newRange);
                          redisService.updateHistoryCoverage(instrument.toString(), finalFrom, finalTo);
                      }
                  } catch (Exception e) {
                      log.error("Failed to extract history for {}: {}", instrument, e.getMessage());
                  }
              }
              
              // 3. 所有提取完成后，发送一次统一通知
              log.info("Coalesced history sync completed for all requested instruments. Sending notification.");
              redisService.notifyHistoryUpdated("ALL"); // 使用 ALL 标志告知客户端全量刷新
              
          } catch (Exception e) {
              log.error("Error in coalesced history sync worker", e);
          }
      });
  }

  /**
   * 实际执行全量持仓同步的核心逻辑
   */
  private Void executeFullPositionSync() {
      if (context == null || context.getEngine() == null) {
          return null;
      }
      try {
          List<IOrder> orders = context.getEngine().getOrders();
          List<phiner.de5.net.gateway.dto.PositionDTO> positions = new java.util.ArrayList<>();
          for (IOrder order : orders) {
              if (order.getState() == IOrder.State.FILLED) {
                  positions.add(new phiner.de5.net.gateway.dto.PositionDTO(order));
              }
          }
          log.info("Executing coalesced full position sync for {} filled orders.", positions.size());
          redisService.refreshPositionsHash(positions);
      } catch (Exception e) {
          log.error("Failed to perform full position sync", e);
      }
      return null;
  }

  public void handleOrdersHistoryRequest(OrdersHistoryRequest request) {
    if (context == null || context.getHistory() == null || context.getEngine() == null) {
        log.warn("Context not initialized, cannot handle orders history request");
        return;
    }

    String instrumentName = request.getInstrument();
    if (instrumentName == null || instrumentName.isEmpty()) {
        log.warn("Instrument is mandatory for orders history request");
        return;
    }

    Instrument instrument = parseInstrument(instrumentName);
    if (instrument == null) {
        log.error("Invalid instrument in history request: {}", instrumentName);
        redisService.publishError("Invalid instrument: " + instrumentName);
        return;
    }

    try {
        long requestedStart = request.getStartTime();
        long now = context.getTime();
        long requestedEnd = request.getEndTime() > 0 ? request.getEndTime() : now;

        // 1. 优先从内存缓存获取，如果不存在则从 Redis 回源
        long[] coverage = historyCoverageCache.computeIfAbsent(instrumentName, k -> {
            long[] fromRedis = redisService.getHistoryCoverage(k);
            if (fromRedis != null) {
                log.info("Populated history coverage cache from Redis for {}: [{}, {}]", k, fromRedis[0], fromRedis[1]);
            }
            return fromRedis;
        });
        
        long effectiveFrom;
        long effectiveTo = requestedEnd;

        if (coverage == null) {
            // 从未同步过，直接请求全量
            effectiveFrom = requestedStart;
            log.info("No history coverage for {}, fetching full range: [{}, {}]", instrumentName, effectiveFrom, effectiveTo);
        } else {
            long cachedMin = coverage[0];
            long cachedMax = coverage[1];

            if (requestedStart < cachedMin) {
                // 1. 如果请求的开始时间比缓存的最早时间还要早，我们需要补齐 [requestedStart, now]
                // 这里为了简单，直接从请求的最早时间拉取到最新，利用 Redis Hash 自动去重
                effectiveFrom = requestedStart;
                log.info("Extending history past for {}: requested {} < cached {}, fetching [{}, {}]", 
                         instrumentName, requestedStart, cachedMin, effectiveFrom, effectiveTo);
            } else {
                // 2. 如果请求的起始时间已在缓存范围内，我们只需要做增量更新 [cachedMax, now]
                // 或者是检查是否需要更新到请求的 endTime
                effectiveFrom = cachedMax;
                if (effectiveTo <= cachedMax) {
                    log.info("Requested history range for {} [{}, {}] is already fully cached [{}, {}].", 
                             instrumentName, requestedStart, requestedEnd, cachedMin, cachedMax);
                    // 即使不请求 API，也触发一次通知，确保客户端知道可以从缓存读了
                    redisService.notifyHistoryUpdated(instrumentName);
                    return;
                }
                log.info("Incremental history sync for {}: fetching from cachedMax {} to {}", 
                         instrumentName, cachedMax, effectiveTo);
            }
        }

        // 进入防抖合并队列
        scheduleDebouncedHistorySync(instrument, effectiveFrom, effectiveTo);
        
    } catch (Exception e) {
        log.error("Invalid instrument in history request: {}", instrumentName, e);
    }
  }
}
