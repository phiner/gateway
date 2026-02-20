
package phiner.de5.net.gateway.strategy;

import static org.mockito.Mockito.*;

import com.dukascopy.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import phiner.de5.net.gateway.KLineManager;
import phiner.de5.net.gateway.TickManager;
import phiner.de5.net.gateway.dto.InstrumentInfoDTO;
import phiner.de5.net.gateway.config.ForexProperties;
import phiner.de5.net.gateway.request.*;
import phiner.de5.net.gateway.service.RedisService;

@SuppressWarnings("null")
public class TradingStrategyTest {

  @Mock
  private IContext context;

  @Mock
  private IEngine engine;

  @Mock
  private TickManager tickManager;

  @Mock
  private KLineManager kLineManager;

  @Mock
  private RedisService redisService;

  @Mock
  private ForexProperties forexProperties;

  private TradingStrategy tradingStrategy;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    tradingStrategy = new TradingStrategy(tickManager, kLineManager, redisService, forexProperties);
    tradingStrategy.onStart(context);
    when(context.getEngine()).thenReturn(engine);
  }

  @Test
  public void testHandleInstrumentInfoRequest_success() throws Exception {
    // Given
    String instrumentName = "EUR/USD";
    String requestId = "test-request-id";
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setInstrument(instrumentName);
    request.setRequestId(requestId);

    Instrument mockInstrument = mock(Instrument.class);
    ICurrency primaryCurrency = mock(ICurrency.class);
    ICurrency secondaryCurrency = mock(ICurrency.class);

    when(mockInstrument.name()).thenReturn(instrumentName);
    when(mockInstrument.getPrimaryJFCurrency()).thenReturn(primaryCurrency);
    when(mockInstrument.getSecondaryJFCurrency()).thenReturn(secondaryCurrency);
    when(primaryCurrency.getCurrencyCode()).thenReturn("EUR");
    when(secondaryCurrency.getCurrencyCode()).thenReturn("USD");
    when(mockInstrument.getPipValue()).thenReturn(0.0001);

    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

      // When
      tradingStrategy.handleInstrumentInfoRequest(request);

      // Then
      verify(redisService).publishInstrumentInfo(any(InstrumentInfoDTO.class), eq(requestId));
      verify(redisService, never()).publishError(anyString());
    }
  }

  @Test
  public void testHandleInstrumentInfoRequest_nullInstrumentName() {
    // Given
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setRequestId("test-request-id");

    // When
    tradingStrategy.handleInstrumentInfoRequest(request);

    // Then
    verify(redisService).publishError("Instrument name is null in InstrumentInfoRequest.");
    verify(redisService, never()).publishInstrumentInfo(any(), any());
  }

  @Test
  public void testHandleInstrumentInfoRequest_nullRequestId() {
    // Given
    String instrumentName = "EUR/USD";
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setInstrument(instrumentName);

    // When
    tradingStrategy.handleInstrumentInfoRequest(request);

    // Then
    verify(redisService).publishError("Request ID is null in InstrumentInfoRequest for instrument: " + instrumentName);
    verify(redisService, never()).publishInstrumentInfo(any(), any());
  }

  @Test
  public void testHandleInstrumentInfoRequest_instrumentNotFound() throws Exception {
    // Given
    String instrumentName = "UNKNOWN/USD";
    String requestId = "test-request-id";
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setInstrument(instrumentName);
    request.setRequestId(requestId);

    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(null);

      // When
      tradingStrategy.handleInstrumentInfoRequest(request);

      // Then
      verify(redisService).publishError("Instrument not found: " + instrumentName);
      verify(redisService, never()).publishInstrumentInfo(any(), any());
    }
  }

  @Test
  public void testHandleInstrumentInfoRequest_nullCurrencyInfo() throws Exception {
    // Given
    String instrumentName = "EUR/USD";
    String requestId = "test-request-id";
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setInstrument(instrumentName);
    request.setRequestId(requestId);

    Instrument mockInstrument = mock(Instrument.class);
    when(mockInstrument.name()).thenReturn(instrumentName);
    when(mockInstrument.getPrimaryJFCurrency()).thenReturn(null); // Simulate API returning null

    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

      // When
      tradingStrategy.handleInstrumentInfoRequest(request);

      // Then
      verify(redisService).publishError("Error fetching instrument info for " + instrumentName + ": received null values from API for name or currency objects.");
      verify(redisService, never()).publishInstrumentInfo(any(), any());
    }
  }

  @Test
  public void testExecuteMarketOrder_buy() throws JFException {
    // Given
    String instrumentName = "EUR/USD";
    Instrument mockInstrument = mock(Instrument.class);
    OpenMarketOrderRequest request = new OpenMarketOrderRequest(
        instrumentName, 0.1, MarketOrderType.BUY, "label-1", 5.0, 1.1234, 1.1236);

    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

      // When
      tradingStrategy.executeMarketOrder(request);

      // Then
      verify(engine).submitOrder(eq("label-1"), eq(mockInstrument), eq(IEngine.OrderCommand.BUY), eq(0.1), eq(0.0), eq(5.0), eq(1.1234), eq(1.1236));
    }
  }

  @Test
  public void testExecuteMarketOrder_sellNoLabel() throws JFException {
    // Given
    String instrumentName = "AUD/JPY";
    Instrument mockInstrument = mock(Instrument.class);
    OpenMarketOrderRequest request = new OpenMarketOrderRequest(
        instrumentName, 0.2, MarketOrderType.SELL, "", 10.0, 95.12, 96.55);
    ArgumentCaptor<String> labelCaptor = ArgumentCaptor.forClass(String.class);

    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

      // When
      tradingStrategy.executeMarketOrder(request);

      // Then
      verify(engine).submitOrder(labelCaptor.capture(), eq(mockInstrument), eq(IEngine.OrderCommand.SELL), eq(0.2), eq(0.0), eq(10.0), eq(95.12), eq(96.55));
      assert(labelCaptor.getValue().startsWith("Order-"));
    }
  }

  @Test
  public void testCloseMarketOrder_orderFound() throws JFException {
    // Given
    String orderId = "order-to-close";
    CloseMarketOrderRequest request = new CloseMarketOrderRequest(orderId);
    IOrder mockOrder = mock(IOrder.class);
    when(engine.getOrderById(orderId)).thenReturn(mockOrder);

    // When
    tradingStrategy.closeMarketOrder(request);

    // Then
    verify(mockOrder).close();
  }

  @Test
  public void testCloseMarketOrder_orderNotFound() throws JFException {
    // Given
    String orderId = "non-existent-order";
    CloseMarketOrderRequest request = new CloseMarketOrderRequest(orderId);
    when(engine.getOrderById(orderId)).thenReturn(null);
    IOrder mockOrder = mock(IOrder.class);

    // When
    tradingStrategy.closeMarketOrder(request);

    // Then
    verify(mockOrder, never()).close();
  }

 @Test
  public void testSubmitOrder() throws JFException {
      // Given
      String instrumentName = "GBP/USD";
      Instrument mockInstrument = mock(Instrument.class);
      SubmitOrderRequest request = new SubmitOrderRequest();
      request.setInstrument(instrumentName);
      request.setOrderCommand("BUYLIMIT");
      request.setAmount(0.05);
      request.setPrice(1.2500);
      request.setLabel("label-2");
      request.setStopLossPrice(1.2400);
      request.setTakeProfitPrice(1.2600);

      try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
        mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

        // When
        tradingStrategy.submitOrder(request);

        // Then
        verify(engine).submitOrder(eq("label-2"), eq(mockInstrument), eq(IEngine.OrderCommand.BUYLIMIT), eq(0.05), eq(1.2500), eq(0.0), eq(1.2400), eq(1.2600));
      }
  }

  @Test
  public void testModifyOrder() throws JFException {
      // Given
      String orderId = "order-to-modify";
      ModifyOrderRequest request = new ModifyOrderRequest();
      request.setOrderId(orderId);
      request.setStopLossPrice(1.1300);
      request.setTakeProfitPrice(1.1400);
      IOrder mockOrder = mock(IOrder.class);
      when(engine.getOrderById(orderId)).thenReturn(mockOrder);

      // When
      tradingStrategy.modifyOrder(request);

      // Then
      verify(mockOrder).setStopLossPrice(1.1300);
      verify(mockOrder).setTakeProfitPrice(1.1400);
  }

  @Test
  public void testCancelOrder_orderOpened() throws JFException {
      // Given
      String orderId = "order-to-cancel";
      CancelOrderRequest request = new CancelOrderRequest();
      request.setOrderId(orderId);
      IOrder mockOrder = mock(IOrder.class);
      when(engine.getOrderById(orderId)).thenReturn(mockOrder);
      when(mockOrder.getState()).thenReturn(IOrder.State.OPENED);

      // When
      tradingStrategy.cancelOrder(request);

      // Then
      verify(mockOrder).close();
  }

  @Test
  public void testCancelOrder_orderFilled() throws JFException {
      // Given
      String orderId = "order-filled";
      CancelOrderRequest request = new CancelOrderRequest();
      request.setOrderId(orderId);
      IOrder mockOrder = mock(IOrder.class);
      when(engine.getOrderById(orderId)).thenReturn(mockOrder);
      when(mockOrder.getState()).thenReturn(IOrder.State.FILLED);

      // When
      tradingStrategy.cancelOrder(request);

      // Then
      verify(mockOrder, never()).close();
  }
}
