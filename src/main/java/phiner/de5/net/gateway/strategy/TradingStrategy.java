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
  private ExecutorService executor; // original executor, perhaps for orders?
  private ExecutorService eventProcessor; // New executor for JForex events

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
    // Initialize the event processor for asynchronous handling of ticks/bars/messages
    this.eventProcessor = Executors.newSingleThreadExecutor();

    if (context != null) {
      // Subscribe to instruments from configuration
      if (forexProperties.getInstruments() != null && !forexProperties.getInstruments().isEmpty()) {
        Set<Instrument> instrumentsToSubscribe = forexProperties.getInstruments().stream()
            .map(String::trim)
            .map(name -> {
              try {
                Instrument inst = Instrument.fromString(name);
                log.info("Converted string '{}' to instrument: {}", name, inst);
                return inst;
              } catch (Exception e) {
                log.error("Failed to convert '{}' to instrument", name, e);
                redisService.publishError("Invalid instrument name in configuration: " + name);
                return null;
              }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!instrumentsToSubscribe.isEmpty()) {
          this.subscribedInstruments.addAll(instrumentsToSubscribe);
          log.info("Subscribing to instruments: {}", this.subscribedInstruments);
          context.setSubscribedInstruments(this.subscribedInstruments, true);
          String subscribed = instrumentsToSubscribe.stream()
              .map(Instrument::name)
              .collect(Collectors.joining(", "));
          log.info("Successfully subscribed to instruments: {}", subscribed);
          redisService.publishInfo("Successfully subscribed to instruments: " + subscribed);
        } else {
          log.warn("No valid instruments to subscribe to!");
        }
      }

      // Parse and store configured periods
      if (forexProperties.getPeriods() != null && !forexProperties.getPeriods().isEmpty()) {
        Set<Period> periodsToProcess = forexProperties.getPeriods().stream()
            .map(String::trim)
            .map(name -> {
                try {
                    return Period.valueOf(name);
                } catch (IllegalArgumentException e) {
                    redisService.publishError("Invalid period name in configuration: " + name);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        this.configuredPeriods.addAll(periodsToProcess);
        String periods = periodsToProcess.stream().map(Period::toString).collect(Collectors.joining(", "));
        log.info("Will process bars for periods: {}", periods);
        redisService.publishInfo("Will process bars for periods: " + periods);

        // Preload historical data ASYNCHRONOUSLY to avoid blocking JForex start thread
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
              log.info("Async History Preloader: Waiting for instrument subscriptions to be confirmed by server...");
              long startWait = System.currentTimeMillis();
              // Poll until all requested instruments are reported as subscribed by the server
              while (!context.getSubscribedInstruments().containsAll(this.subscribedInstruments)) {
                  if (System.currentTimeMillis() - startWait > 30000) {
                      log.warn("Async History Preloader: Timeout (30s) waiting for all instruments to subscribe. Proceeding with available ones.");
                      break;
                  }
                  try {
                      Thread.sleep(200);
                  } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                  }
              }
              log.info("Async History Preloader: Subscriptions confirmed in {}ms. Starting history fetch.", System.currentTimeMillis() - startWait);
              IHistory history = context.getHistory();
              log.info("Async History Preloader: klineStorageLimit: {}, contextTime: {}", klineStorageLimit, context.getTime());
              for (Instrument instrument : this.subscribedInstruments) {
                String instrumentName = instrument.toString();
                for (Period period : this.configuredPeriods) {
                  try {
                    log.info("Async History Preloader: Requesting historical data for {} and period {}", instrumentName, period);
                    // Calculate range: end at the start of the last COMPLETED bar
                    long to = history.getPreviousBarStart(period, context.getTime());
                    long from = to - (klineStorageLimit * period.getInterval());
                    
                    // Use the from/to signature which is sometimes more reliable
                    List<IBar> bars = history.getBars(instrument, period, OfferSide.ASK, from, to);
                    
                    if (bars != null && !bars.isEmpty()) {
                        log.info("Async History Preloader: Received {} historical bars for {} and period {}", bars.size(), instrumentName, period);
                        if (instrumentName != null) {
                            for (IBar bar : bars) {
                                BarDTO barDTO = new BarDTO(instrumentName, period.toString(), bar);
                                // Process the bar using the sequential event processor
                                eventProcessor.submit(() -> kLineManager.onBar(instrumentName, barDTO));
                            }
                        }
                    } else if (bars != null) {
                        log.warn("Async History Preloader: Received 0 bars for {} and period {} in range {} to {}", instrumentName, period, from, to);
                    } else {
                        log.warn("Async History Preloader: Received null bars for {} and period {}", instrumentName, period);
                    }
                  } catch (Exception e) {
                    log.error("Async History Preloader: Failed to preload historical data for {} and period {}", instrumentName, period, e);
                  }
                }
              }
            } catch (Exception e) {
              log.error("Async History Preloader encountered a general error", e);
            }
        });
      }

      redisService.publishGatewayStatus(
          new GatewayStatusDTO(
              "CONNECTED", "Trading strategy started and connected to Dukascopy."));
      redisService.publishInfo("Trading strategy started.");
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
            log.info("Processing live bar: {} {} Time: {}", instrumentName, periodName, bidBar.getTime());
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
            if (order != null && order.getState() == IOrder.State.FILLED) {
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
      eventProcessor.submit(() -> redisService.publishAccountStatus(account.getBalance(), account.getEquity()));
    }
  }

  @Override
  public void onStop() {
    if (executor != null) {
        executor.shutdown();
    }
    if (eventProcessor != null) {
        eventProcessor.shutdown();
    }
    redisService.publishGatewayStatus(
        new GatewayStatusDTO("DISCONNECTED", "Trading strategy stopped."));
    redisService.publishInfo("Trading strategy stopped.");
  }

  public IContext getContext() {
    return context;
  }

  public void setExecutor(ExecutorService executor) {
      this.executor = executor;
  }

  public void executeMarketOrder(OpenMarketOrderRequest request) throws JFException {
        Instrument instrument = Instrument.fromString(request.getInstrument());
        IEngine.OrderCommand command = (request.getOrderType() == MarketOrderType.BUY) ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL;
        double amount = request.getAmount();
        String label = (request.getLabel() != null && !request.getLabel().isEmpty()) ? request.getLabel() : getNewLabel();

        context.getEngine().submitOrder(
                label,
                instrument,
                command,
                amount,
                0, // price
                request.getSlippage() != null ? request.getSlippage() : 0, // slippage
                request.getStopLossPrice() != null ? request.getStopLossPrice() : 0, // stopLossPrice
                request.getTakeProfitPrice() != null ? request.getTakeProfitPrice() : 0 // takeProfitPrice
        );
    }

    public void closeMarketOrder(CloseMarketOrderRequest request) throws JFException {
        IOrder order = context.getEngine().getOrderById(request.getOrderId());
        if (order != null) {
            order.close();
        }
    }

    public void submitOrder(SubmitOrderRequest request) throws JFException {
        Instrument instrument = Instrument.fromString(request.getInstrument());
        IEngine.OrderCommand command = IEngine.OrderCommand.valueOf(request.getOrderCommand());
        String label = (request.getLabel() != null && !request.getLabel().isEmpty()) ? request.getLabel() : getNewLabel();

        double stopLossPrice = request.getStopLossPrice();
        double takeProfitPrice = request.getTakeProfitPrice();

        context.getEngine().submitOrder(
                label,
                instrument,
                command,
                request.getAmount(),
                request.getPrice(),
                0, // slippage for pending orders is not applicable in the same way
                stopLossPrice,
                takeProfitPrice
        );
    }

    public void modifyOrder(ModifyOrderRequest request) throws JFException {
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
        }
    }

    public void cancelOrder(CancelOrderRequest request) throws JFException {
        IOrder order = context.getEngine().getOrderById(request.getOrderId());
        if (order != null && order.getState() == IOrder.State.OPENED) {
            order.close();
        }
    }

    public void handleInstrumentInfoRequest(@NonNull InstrumentInfoRequest request) {
        String instrumentName = request.getInstrument();
        if (instrumentName == null) {
            redisService.publishError("Instrument name is null in InstrumentInfoRequest.");
            return;
        }

        String requestId = request.getRequestId();
        if (requestId == null) {
            redisService.publishError("Request ID is null in InstrumentInfoRequest for instrument: " + instrumentName);
            return;
        }

        try {
            Instrument instrument = Instrument.fromString(instrumentName);
            if (instrument == null) {
                redisService.publishError("Instrument not found: " + instrumentName);
                return;
            }

            String name = instrument.toString();
            ICurrency primaryCurrency = instrument.getPrimaryJFCurrency();
            ICurrency secondaryCurrency = instrument.getSecondaryJFCurrency();

            if (name == null || primaryCurrency == null || secondaryCurrency == null) {
                redisService.publishError("Error fetching instrument info for " + instrumentName + ": received null values from API for name or currency objects.");
                return;
            }

            String primaryCode = primaryCurrency.getCurrencyCode();
            String secondaryCode = secondaryCurrency.getCurrencyCode();

            if (primaryCode == null || secondaryCode == null) {
                redisService.publishError("Error fetching instrument info for " + instrumentName + ": currency code is null.");
                return;
            }

            String currency = primaryCode + "/" + secondaryCode;
            InstrumentInfoDTO infoDTO = new InstrumentInfoDTO(
                    name,
                    currency,
                    instrument.getPipValue(),
                    instrument.getPipValue() / 10,
                    name
            );

            redisService.publishInstrumentInfo(infoDTO, requestId);

        } catch (Exception e) {
            redisService.publishError("Error fetching instrument info for " + instrumentName + ": " + e.getMessage());
        }
    }

  private String getNewLabel() {
    return "Order-" + System.currentTimeMillis();
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
