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
import phiner.de5.net.gateway.request.CloseMarketOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class OrderCloseListenerTest {

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private OrderCloseListener listener;

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
        CloseMarketOrderRequest request = new CloseMarketOrderRequest("test-id");
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CloseMarketOrderRequest.class)).thenReturn(request);

        // When
        listener.onMessage(message, null);

        // Then
        verify(tradingStrategy).closeMarketOrder(request);
        verifyNoInteractions(redisService);
    }

    // Removed testOnMessage_jFException

    @Test
    public void testOnMessage_genericException() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);
        RuntimeException testException = new RuntimeException("Test RuntimeException");

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CloseMarketOrderRequest.class)).thenThrow(testException);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verify(redisService).publishError("无法处理平仓市场订单请求: " + testException.getMessage());
    }

    @Test
    public void testOnMessage_nullRequest() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        mockedDecoder.when(() -> MsgpackDecoder.decode(body, CloseMarketOrderRequest.class)).thenReturn(null);

        // When
        listener.onMessage(message, null);

        // Then
        verifyNoInteractions(tradingStrategy);
        verifyNoInteractions(redisService);
    }
}
