package phiner.de5.net.gateway.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import com.dukascopy.api.JFException;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.request.CancelOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class OrderCancelListenerTest {

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private OrderCancelListener listener;

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
        CancelOrderRequest request = new CancelOrderRequest();
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CancelOrderRequest.class)).thenReturn(request);

        // When
        listener.onMessage(message, null);

        // Then
        verify(tradingStrategy).cancelOrder(request);
        verifyNoInteractions(redisService);
    }

    @Test
    public void testOnMessage_exception() {
        // Given
        byte[] body = "test body".getBytes();
        RuntimeException testException = new RuntimeException("Test RuntimeException");
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CancelOrderRequest.class)).thenThrow(testException);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verify(redisService).publishError("Failed to cancel order: " + testException.getMessage());
    }

    @Test
    public void testOnMessage_nullRequest() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CancelOrderRequest.class)).thenReturn(null);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verifyNoInteractions(redisService);
    }
}
