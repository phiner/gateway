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

    private OrderOpenListener listener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new OrderOpenListener(tradingStrategy, redisService);
    }

    @Test
    public void testOnMessage_success() throws JFException {
        byte[] body = "test body".getBytes();
        OpenMarketOrderRequest request = new OpenMarketOrderRequest("EURUSD", 0.1, MarketOrderType.BUY, "test-label", 5.0, 1.1234, 1.1236);
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, OpenMarketOrderRequest.class)).thenReturn(request);
            listener.onMessage(message, null);
            verify(tradingStrategy).executeMarketOrder(request);
            verifyNoInteractions(redisService);
        }
    }

    @Test
    public void testOnMessage_genericException() {
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, OpenMarketOrderRequest.class)).thenThrow(new RuntimeException("Test RuntimeException"));
            listener.onMessage(message, null);
            verifyNoInteractions(tradingStrategy);
            verify(redisService).publishError(anyString());
        }
    }

    @Test
    public void testOnMessage_nullRequest() {
        byte[] body = "test body".getBytes();
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, OpenMarketOrderRequest.class)).thenReturn(null);
            listener.onMessage(message, null);
            verifyNoInteractions(tradingStrategy);
            verifyNoInteractions(redisService);
        }
    }
}
