
package phiner.de5.net.gateway.strategy;

import com.dukascopy.api.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.KLineManager;
import phiner.de5.net.gateway.TickManager;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.dto.GatewayStatusDTO;
import phiner.de5.net.gateway.dto.InstrumentInfoDTO;
import phiner.de5.net.gateway.request.*;
import phiner.de5.net.gateway.service.RedisService;

@Component
public class TradingStrategy implements IStrategy {

  private IContext context;
  private ExecutorService executor;

  private final Set<Instrument> subscribedInstruments = new HashSet<>();
  private final Set<Period> configuredPeriods = new HashSet<>();
  private final TickManager tickManager;
  private final KLineManager kLineManager;
  private final RedisService redisService;

  @Value("${forex.instruments}")
  private String instrumentsValue;

  @Value("${forex.periods}")
  private String periodsValue;

  public TradingStrategy(
      TickManager tickManager, KLineManager kLineManager, RedisService redisService) {
    this.tickManager = tickManager;
    this.kLineManager = kLineManager;
    this.redisService = redisService;
  }

  @Override
  public void onStart(IContext context) {
    this.context = context;
    if (this.executor == null) {
        this.executor = Executors.newSingleThreadExecutor();
    }
    if (context != null) {
      // Subscribe to instruments from configuration
      if (instrumentsValue != null && !instrumentsValue.isEmpty()) {
        Set<Instrument> instrumentsToSubscribe = Arrays.stream(instrumentsValue.split(","))
            .map(String::trim)
            .map(name -> {
              try {
                return Instrument.fromString(name);
              } catch (Exception e) {
                redisService.publishError("Invalid instrument name in configuration: " + name);
                return null;
              }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (!instrumentsToSubscribe.isEmpty()) {
          this.subscribedInstruments.addAll(instrumentsToSubscribe);
          context.setSubscribedInstruments(this.subscribedInstruments, true);
          String subscribed = instrumentsToSubscribe.stream()
              .map(Instrument::name)
              .collect(Collectors.joining(", "));
          redisService.publishInfo("Successfully subscribed to instruments: " + subscribed);
        }
      }

      // Parse and store configured periods
      if (periodsValue != null && !periodsValue.isEmpty()) {
        Set<Period> periodsToProcess = Arrays.stream(periodsValue.split(","))
            .map(String::trim)
            .map(name -> {
                try {
                    // Attempt to convert to Period enum
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
        redisService.publishInfo("Will process bars for periods: " + periods);
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
      String instrumentName = instrument.name();
      if (instrumentName != null) {
        tickManager.onTick(instrumentName, tick);
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
      String instrumentName = instrument.name();
      String periodName = period.toString();
      if (instrumentName != null && periodName != null) {
        BarDTO barDTO = new BarDTO(instrumentName, periodName, bidBar);
        kLineManager.onBar(instrumentName, barDTO);
      }
    }
  }

  @Override
  public void onMessage(IMessage message) {
    if (message != null) {
      redisService.publishOrderEvent(message);
      try {
        IOrder order = message.getOrder();
        if (order != null && order.getState() == IOrder.State.FILLED) {
          System.out.println("Order filled: " + order.getLabel());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void onAccount(IAccount account) {
    if (account != null) {
      redisService.publishAccountStatus(account.getBalance(), account.getEquity());
    }
  }

  @Override
  public void onStop() {
    executor.shutdown();
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

            String name = instrument.name();
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
