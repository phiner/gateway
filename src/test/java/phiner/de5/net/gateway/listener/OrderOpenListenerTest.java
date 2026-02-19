package phiner.de5.net.gateway.listener;

import com.dukascopy.api.JFException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.MarketOrderType;
import phiner.de5.net.gateway.request.OpenMarketOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class OrderOpenListenerTest {

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private OrderOpenListener listener;

    private MockedStatic<MsgpackDecoder> mockedDecoder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockedDecoder = mockStatic(MsgpackDecoder.class);
    }

    @AfterEach
    public void tearDown() {
        mockedDecoder.close();
    }

    @Test
    public void testOnMessage_success() throws JFException {
        // Given
        byte[] body = "test body".getBytes();
        OpenMarketOrderRequest request = new OpenMarketOrderRequest("EURUSD", 0.1, MarketOrderType.BUY, "test-label", 5.0, 1.1234, 1.1236);
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, OpenMarketOrderRequest.class)).thenReturn(request);

        // When
        listener.onMessage(message, null);

        // Then
        verify(tradingStrategy).executeMarketOrder(request);
        verifyNoInteractions(redisService);
    }

    @Test
    public void testOnMessage_jFException() throws JFException {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);
        JFException testException = new JFException("Test JFException");

        OpenMarketOrderRequest request = new OpenMarketOrderRequest("EURUSD", 0.1, MarketOrderType.BUY, "test-label", 5.0, 1.1234, 1.1236);
        mockedDecoder.when(() -> MsgpackDecoder.decode(body, OpenMarketOrderRequest.class)).thenReturn(request);
        doThrow(testException).when(tradingStrategy).executeMarketOrder(request);

        // When
        listener.onMessage(message, null);

        // Then
        verify(redisService).publishError("Failed to execute open market order: " + testException.getMessage());
    }

    @Test
    public void testOnMessage_genericException() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);
        RuntimeException testException = new RuntimeException("Test RuntimeException");

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, OpenMarketOrderRequest.class)).thenThrow(testException);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verify(redisService).publishError("Failed to process open market order request: " + testException.getMessage());
    }

    @Test
    public void testOnMessage_nullRequest() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, OpenMarketOrderRequest.class)).thenReturn(null);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verifyNoInteractions(redisService);
    }
}
