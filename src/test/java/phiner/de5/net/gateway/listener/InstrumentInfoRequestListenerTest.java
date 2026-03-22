package phiner.de5.net.gateway.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import phiner.de5.net.gateway.MsgpackUtil;
import phiner.de5.net.gateway.request.InstrumentInfoRequest;
import phiner.de5.net.gateway.service.RedisService;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class InstrumentInfoRequestListenerTest {

    @Mock
    private TradingStrategy tradingStrategy;

    @Mock
    private RedisService redisService;

    private InstrumentInfoRequestListener listener;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new InstrumentInfoRequestListener(tradingStrategy, redisService);
    }

    @Test
    public void testOnMessage_success() {
        // Given
        byte[] body = "test body".getBytes();
        InstrumentInfoRequest request = new InstrumentInfoRequest();
        Message message = new DefaultMessage("channel".getBytes(), body);

        try (MockedStatic<MsgpackUtil> mockedUtil = mockStatic(MsgpackUtil.class)) {
            mockedUtil.when(() -> MsgpackUtil.decode(body, InstrumentInfoRequest.class)).thenReturn(request);

            // When
            listener.onMessage(message, null);

            // Then
            verify(tradingStrategy).handleInstrumentInfoRequest(request);
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
            mockedUtil.when(() -> MsgpackUtil.decode(body, InstrumentInfoRequest.class)).thenThrow(testException);

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
            mockedUtil.when(() -> MsgpackUtil.decode(body, InstrumentInfoRequest.class)).thenReturn(null);

            // When
            listener.onMessage(message, null);

            // Then
            verifyNoInteractions(tradingStrategy);
            verifyNoInteractions(redisService);
        }
    }
}
