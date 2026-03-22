package phiner.de5.net.gateway.listener;

import com.dukascopy.api.JFException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import phiner.de5.net.gateway.MsgpackUtil;
import phiner.de5.net.gateway.request.ModifyOrderRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class OrderModifyListenerTest {

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private RedisService redisService;

    private OrderModifyListener listener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new OrderModifyListener(tradingStrategy, redisService);
    }

    @Test
    public void testOnMessage_success() throws JFException {
        // Given
        byte[] body = "test body".getBytes();
        ModifyOrderRequest request = new ModifyOrderRequest();
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, ModifyOrderRequest.class)).thenReturn(request);

            // When
            listener.onMessage(message, null);

            // Then
            verify(tradingStrategy).modifyOrder(request);
            verifyNoInteractions(redisService);
        }
    }

    @Test
    public void testOnMessage_exception() {
        // Given
        byte[] body = "test body".getBytes();
        RuntimeException testException = new RuntimeException("Test RuntimeException");
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, ModifyOrderRequest.class)).thenThrow(testException);

            // When
            listener.onMessage(message, null);

            // Then
            verifyNoInteractions(tradingStrategy);
            verify(redisService).publishError(anyString());
        }
    }

    @Test
    public void testOnMessage_nullRequest() {
        // Given
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, ModifyOrderRequest.class)).thenReturn(null);

            // When
            listener.onMessage(message, null);

            // Then
            verifyNoInteractions(tradingStrategy);
            verifyNoInteractions(redisService);
        }
    }
}
