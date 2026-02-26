package phiner.de5.net.gateway.strategy;

import com.dukascopy.api.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import phiner.de5.net.gateway.config.ForexProperties;
import phiner.de5.net.gateway.KLineManager;
import phiner.de5.net.gateway.TickManager;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.dto.GatewayStatusDTO;
import phiner.de5.net.gateway.dto.InstrumentInfoDTO;
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

  private final Set<Instrument> subscribedInstruments = new HashSet<>();
  private final Set<Period> configuredPeriods = new HashSet<>();
  private final TickManager tickManager;
  private final KLineManager kLineManager;
  private final RedisService redisService;
  private final ForexProperties forexProperties;

  @Value("${gateway.kline.storage-limit}")
  private int klineStorageLimit;

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

    if (context != null) {
      // 从配置中订阅交易产品
      if (forexProperties.getInstruments() != null && !forexProperties.getInstruments().isEmpty()) {
        Set<Instrument> instrumentsToSubscribe = forexProperties.getInstruments().stream()
            .map(String::trim)
            .map(name -> {
              try {
                Instrument inst = Instrument.fromString(name);
                log.info("已将字符串 '{}' 转换为产品: {}", name, inst);
                return inst;
              } catch (Exception e) {
                log.error("无法将 '{}' 转换为产品", name, e);
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
                    List<IBar> bars = history.getBars(instrument, period, OfferSide.ASK, from, to);
                    
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
            if (order != null && message.getType() == IMessage.Type.ORDER_FILL_OK) {
              log.info("Order filled: {}", order.getLabel());
            }
          } catch (Exception e) {
            log.error("Error processing order message", e);
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
  @Scheduled(fixedRate = 5000)
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

  public void executeMarketOrder(OpenMarketOrderRequest request) {
        runTask(() -> {
        Instrument instrument = Instrument.fromString(request.getInstrument());
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
            Instrument instrument = Instrument.fromString(request.getInstrument());
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
                    takeProfitPrice
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
                    order.setStopLossPrice(stopLossPrice);
                }
                if (takeProfitPrice > 0) {
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

            Instrument instrument = Instrument.fromString(instrumentName);
            if (instrument == null) {
                redisService.publishError("Instrument not found: " + instrumentName);
                return null;
            }

            String name = instrument.toString();
            ICurrency primaryCurrency = instrument.getPrimaryJFCurrency();
            ICurrency secondaryCurrency = instrument.getSecondaryJFCurrency();

            if (name == null || primaryCurrency == null || secondaryCurrency == null) {
                redisService.publishError("Error fetching instrument info for " + instrumentName + ": received null values from API.");
                return null;
            }

            String primaryCode = primaryCurrency.getCurrencyCode();
            String secondaryCode = secondaryCurrency.getCurrencyCode();

            if (primaryCode == null || secondaryCode == null) {
                redisService.publishError("Error fetching instrument info for " + instrumentName + ": currency code is null.");
                return null;
            }

            String currency = primaryCode + "/" + secondaryCode;
            InstrumentInfoDTO infoDTO = new InstrumentInfoDTO(
                    name,
                    currency,
                    java.math.BigDecimal.valueOf(instrument.getPipValue()).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                    java.math.BigDecimal.valueOf(Math.pow(10, -instrument.getTickScale())).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                    name,
                    instrument.getMinTradeAmount()
            );

            redisService.saveInstrumentInfo(infoDTO);
            redisService.publishInstrumentInfo(infoDTO, requestId);
            return null;
        }, "Instrument Info Request [" + request.getInstrument() + "]");
    }

    public void handlePositionsRequest(@NonNull phiner.de5.net.gateway.request.PositionsRequest request) {
        runTask(() -> {
            String requestId = request.getRequestId();
            if (context == null || context.getEngine() == null) {
                redisService.publishError("Engine not available to fetch positions.");
                return null;
            }

            List<IOrder> orders = context.getEngine().getOrders();
            List<phiner.de5.net.gateway.dto.PositionDTO> positions = new java.util.ArrayList<>();
            
            for (IOrder order : orders) {
                if (order.getState() == IOrder.State.FILLED) {
                    positions.add(new phiner.de5.net.gateway.dto.PositionDTO(order));
                }
            }

            phiner.de5.net.gateway.dto.PositionListResponseDTO responseDTO = 
                new phiner.de5.net.gateway.dto.PositionListResponseDTO(positions);
            
            redisService.publishPositions(responseDTO, requestId);
            log.info("Published {} positions for requestId: {}", positions.size(), requestId);
            return null;
        }, "Positions Request");
    }

    @FunctionalInterface
    private interface ITask<T> {
        T onExecute() throws Exception;
    }

    private void runTask(ITask<Void> task, String errorContext) {
        if (context == null) {
            log.error("JForex Context is not initialized. Failed: {}", errorContext);
            redisService.publishError("JForex Context not initialized. " + errorContext);
            return;
        }
        context.executeTask(() -> {
            try {
                return task.onExecute();
            } catch (Exception e) {
                log.error("Exception in JForex thread: {}", errorContext, e);
                redisService.publishError("JForex Thread Error: " + errorContext + " - " + e.getMessage());
                return null;
            }
        });
    }

    private void saveInstrumentDetailsToRedis(@NonNull Instrument instrument) {
        try {
            String name = instrument.toString();
            ICurrency primaryCurrency = instrument.getPrimaryJFCurrency();
            ICurrency secondaryCurrency = instrument.getSecondaryJFCurrency();

            if (name == null || primaryCurrency == null || secondaryCurrency == null) {
                log.error("Failed to fetch full instrument details for {}", instrument);
                return;
            }

            String currency = primaryCurrency.getCurrencyCode() + "/" + secondaryCurrency.getCurrencyCode();
            InstrumentInfoDTO infoDTO = new InstrumentInfoDTO(
                    name,
                    currency,
                    java.math.BigDecimal.valueOf(instrument.getPipValue()).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                    java.math.BigDecimal.valueOf(Math.pow(10, -instrument.getTickScale())).setScale(6, java.math.RoundingMode.HALF_UP).doubleValue(),
                    name,
                    instrument.getMinTradeAmount()
            );
            redisService.saveInstrumentInfo(infoDTO);
        } catch (Exception e) {
            log.error("Error saving instrument info for {} to Redis", instrument, e);
        }
    }

  private String getNewLabel() {
    return "Order_" + System.currentTimeMillis();
  }

  /**
   * 清洗订单标签以符合 JForex 规范（仅允许字母、数字和下划线）。
   * 如果标签以数字开头，则增加前缀以符合规范。
   */
  private String sanitizeLabel(String label) {
      if (label == null || label.isEmpty()) {
          return getNewLabel();
      }
      // 将非法字符替换为下划线
      String sanitized = label.replaceAll("[^a-zA-Z0-9_]", "_");
      
      // JForex 标签不能以数字开头（虽然文档未明确写，但经验表明部分版本有此限制，且用户报错也涉及到此）
      // 实际上报错信息显示 PivotSniper-5Min 其中包含横杠。
      // 如果首字母是数字，增加前缀
      if (Character.isDigit(sanitized.charAt(0))) {
          sanitized = "L_" + sanitized;
      }
      
      return sanitized;
  }

  public Set<Instrument> getSubscribedInstruments() {
    return subscribedInstruments;
  }

  public void subscribeToInstrument(Instrument instrument) {
    if (context != null && subscribedInstruments.add(instrument)) {
      context.setSubscribedInstruments(subscribedInstruments, true);
    }
  }
}
