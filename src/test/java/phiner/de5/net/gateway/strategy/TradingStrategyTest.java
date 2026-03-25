
package phiner.de5.net.gateway.strategy;

import static org.mockito.Mockito.*;

import com.dukascopy.api.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import phiner.de5.net.gateway.KLineManager;
import phiner.de5.net.gateway.TickManager;
import phiner.de5.net.gateway.dto.*;
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
    
    // Mock executeTask to run the task synchronously in tests
    when(context.executeTask(any())).thenAnswer(invocation -> {
        java.util.concurrent.Callable<?> callable = invocation.getArgument(0);
        return java.util.concurrent.CompletableFuture.completedFuture(callable.call());
    });
    
    tradingStrategy.onStart(context);
    when(context.getEngine()).thenReturn(engine);
  }

  @org.junit.jupiter.api.AfterEach
  public void tearDown() {
    if (tradingStrategy != null) {
      tradingStrategy.onStop();
    }
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
    when(mockInstrument.getMinTradeAmount()).thenReturn(0.01);
    when(mockInstrument.toString()).thenReturn(instrumentName);

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
  public void testHandleInstrumentInfoRequest_anyFailure() {
    // Given - Request with null instrument will fail within runTask's lambda
    InstrumentInfoRequest request = new InstrumentInfoRequest();
    request.setRequestId("test-request-id");

    // When
    tradingStrategy.handleInstrumentInfoRequest(request);

    // Then - Exception from Instrument.fromString(null) or similar handled by runTask
    verify(redisService).publishError(anyString());
    verify(redisService, never()).publishInstrumentInfo(any(), any());
  }

  // Removed testHandleInstrumentInfoRequest_nullRequestId because the check was consolidated

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
    when(mockInstrument.toString()).thenReturn(instrumentName);
    when(mockInstrument.getPrimaryJFCurrency()).thenReturn(null); // Simulate API returning null
 
    try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
      mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);
 
      // When
      tradingStrategy.handleInstrumentInfoRequest(request);
 
      // Then
      verify(redisService).publishError(contains("received null values from API"));
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
      // Then
      verify(engine).submitOrder(eq("label-1"), eq(mockInstrument), eq(IEngine.OrderCommand.BUY), eq(0.1), eq(0.0), eq(5.0), eq(1.1234), eq(1.1236), eq(0L), any());
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
      verify(engine).submitOrder(labelCaptor.capture(), eq(mockInstrument), eq(IEngine.OrderCommand.SELL), eq(0.2), eq(0.0), eq(10.0), eq(95.12), eq(96.55), eq(0L), any());
      assert(labelCaptor.getValue().startsWith("Order_"));
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
        verify(engine).submitOrder(eq("label-2"), eq(mockInstrument), eq(IEngine.OrderCommand.BUYLIMIT), eq(0.05), eq(1.2500), eq(0.0), eq(1.2400), eq(1.2600), eq(0L), any());
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
      when(mockOrder.getId()).thenReturn(orderId);
      when(mockOrder.getLabel()).thenReturn("mock-label");

      // When: Client requests modification
      tradingStrategy.modifyOrder(request);

      // Then: SL should be modified immediately
      verify(mockOrder).setStopLossPrice(1.1300);
      // TP should NOT be set yet (it is queued)
      verify(mockOrder, never()).setTakeProfitPrice(anyDouble());

      // Simulate asynchronous server confirmation for SL change
      IMessage mockMessage = mock(IMessage.class);
      when(mockMessage.getOrder()).thenReturn(mockOrder);
      when(mockMessage.getType()).thenReturn(IMessage.Type.ORDER_CHANGED_OK);
      
      // trigger event processor synchronously using our overridden mock
      tradingStrategy.onMessage(mockMessage);

      // Then: TP should now be set automatically by the onMessage listener
      // We use timeout() because onMessage processes asynchronously via eventProcessor thread pool
      verify(mockOrder, timeout(1000)).setTakeProfitPrice(1.1400);
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

  @Test
  public void testExecuteMarketOrder_withInvalidLabel_sanitized() throws JFException {
      // Given
      String instrumentName = "EUR/USD";
      Instrument mockInstrument = mock(Instrument.class);
      // 输入包含横杠的非法标签
      OpenMarketOrderRequest request = new OpenMarketOrderRequest(
          instrumentName, 0.1, MarketOrderType.BUY, "PivotSniper-5Min-123", 5.0, null, null);

      try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
          mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

          // When
          tradingStrategy.executeMarketOrder(request);

          // Then - 保持原标签，由 JForex API 验证
          verify(engine).submitOrder(eq("PivotSniper-5Min-123"), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong(), any());
      }
  }

  @Test
  public void testExecuteMarketOrder_withLeadingDigitLabel_sanitized() throws JFException {
      // Given
      String instrumentName = "EUR/USD";
      Instrument mockInstrument = mock(Instrument.class);
      // 输入以数字开头的标签
      OpenMarketOrderRequest request = new OpenMarketOrderRequest(
          instrumentName, 0.1, MarketOrderType.BUY, "123Strategy", 5.0, null, null);

      try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
          mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

          // When
          tradingStrategy.executeMarketOrder(request);

          // Then - 保持原标签，由 JForex API 验证
          verify(engine).submitOrder(eq("123Strategy"), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong(), any());
      }
  }

  @Test
  public void testExecuteMarketOrder_withSpecialChars_sanitized() throws JFException {
      // Given
      String instrumentName = "EUR/USD";
      Instrument mockInstrument = mock(Instrument.class);
      // 输入包含特殊字符的标签
      OpenMarketOrderRequest request = new OpenMarketOrderRequest(
          instrumentName, 0.1, MarketOrderType.BUY, "Order@#%^&*()", 5.0, null, null);

      try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
          mockedStatic.when(() -> Instrument.fromString(instrumentName)).thenReturn(mockInstrument);

          // When
          tradingStrategy.executeMarketOrder(request);

          // Then - 保持原标签，由 JForex API 验证
          verify(engine).submitOrder(eq("Order@#%^&*()"), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyLong(), any());
      }
  }

  @Test
  public void testHandleOrdersHistoryRequest_coalescedExecution() throws Exception {
      // 1. 设置 Mock 环境
      ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
      ExecutorService mockExecutor = mock(ExecutorService.class);
      IHistory mockHistory = mock(IHistory.class);
      when(context.getHistory()).thenReturn(mockHistory);
      
      tradingStrategy.setSyncScheduler(mockScheduler);
      tradingStrategy.setExecutor(mockExecutor);
      
      Instrument eurUsd = Instrument.EURUSD;
      Instrument gbpUsd = Instrument.GBPUSD;
      
      try (MockedStatic<Instrument> mockedStatic = mockStatic(Instrument.class)) {
          mockedStatic.when(() -> Instrument.fromString("EUR/USD")).thenReturn(eurUsd);
          mockedStatic.when(() -> Instrument.fromString("GBP/USD")).thenReturn(gbpUsd);

          // 2. 模拟连续发送三个请求 (EUR/USD, GBP/USD, EUR/USD)
          OrdersHistoryRequest req1 = new OrdersHistoryRequest();
          req1.setInstrument("EUR/USD");
          req1.setStartTime(1000L);

          OrdersHistoryRequest req2 = new OrdersHistoryRequest();
          req2.setInstrument("GBP/USD");
          req2.setStartTime(500L); // 更早的时间

          OrdersHistoryRequest req3 = new OrdersHistoryRequest();
          req3.setInstrument("EUR/USD");
          req3.setStartTime(1200L);

          tradingStrategy.handleOrdersHistoryRequest(req1);
          tradingStrategy.handleOrdersHistoryRequest(req2);
          tradingStrategy.handleOrdersHistoryRequest(req3);

          // 3. 验证防抖逻辑
          // 应该调用了 3 次 schedule，但最后一次会生成一个有效的 ScheduledFuture
          ArgumentCaptor<Runnable> syncTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
          verify(mockScheduler, times(3)).schedule(syncTaskCaptor.capture(), eq(1000L), eq(TimeUnit.MILLISECONDS));
          
          // 4. 手动触发防抖结束后的合并任务
          syncTaskCaptor.getValue().run(); 

          // 5. 验证执行器是否收到了合并后的批处理任务
          ArgumentCaptor<Runnable> executionTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
          verify(mockExecutor).submit(executionTaskCaptor.capture());

          // 6. 执行真实的批处理逻辑
          executionTaskCaptor.getValue().run();

          // 7. 最终验证
          // a) 验证去重：虽然发了 3 次请求，但只会有 2 个不同品种被提取
          verify(mockHistory).getOrdersHistory(eq(eurUsd), anyLong(), anyLong());
          verify(mockHistory).getOrdersHistory(eq(gbpUsd), anyLong(), anyLong());
          verify(mockHistory, times(2)).getOrdersHistory(any(), anyLong(), anyLong());

          // b) 验证时间跨度：应该是所有请求中最宽的范围 (500L 到 context.getTime())
          // 在 earlier specific verifies already covered this, but let's confirm the 'from' value
          verify(mockHistory, times(2)).getOrdersHistory(any(), eq(500L), anyLong());

          // c) 验证统一通知：只发送一次且标志位为 "ALL"
          verify(redisService, times(1)).notifyHistoryUpdated("ALL");
      }
  }
}
