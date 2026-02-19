package phiner.de5.net.gateway.service;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import phiner.de5.net.gateway.MsgpackDecoder;
import phiner.de5.net.gateway.MsgpackEncoder;
import phiner.de5.net.gateway.dto.*;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
public class RedisServiceTest {

    @Mock
    private RedisTemplate<String, byte[]> redisTemplateBytes;

    @Mock
    private RedisTemplate<String, String> redisTemplateString;

    @Mock
    private ListOperations<String, byte[]> listOperationsBytes;

    @Mock
    private IBar iBar;

    @Mock
    private IMessage iMessage;

    private RedisService redisService;

    private MockedStatic<MsgpackEncoder> mockedEncoder;
    private MockedStatic<MsgpackDecoder> mockedDecoder;

    @BeforeEach
    public void setUp() {
        redisService = new RedisService(redisTemplateBytes, redisTemplateString);
        ReflectionTestUtils.setField(redisService, "klineStorageLimit", 100);
        mockedEncoder = Mockito.mockStatic(MsgpackEncoder.class);
        mockedDecoder = Mockito.mockStatic(MsgpackDecoder.class);
    }

    @AfterEach
    public void tearDown() {
        mockedEncoder.close();
        mockedDecoder.close();
    }

    //<editor-fold desc="KLine Tests">
    @Test
    public void testAddBarToKLine_Success() {
        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        BarDTO bar = new BarDTO("EUR/USD", "1MIN", iBar);
        byte[] barData = "mocked-bar-data".getBytes();
        String expectedKey = "kline:EURUSD:1MIN";
        mockedEncoder.when(() -> MsgpackEncoder.encode(bar)).thenReturn(barData);

        redisService.addBarToKLine(bar);

        verify(listOperationsBytes).leftPush(expectedKey, barData);
        verify(listOperationsBytes).trim(expectedKey, 0, 99);
    }

    @Test
    public void testAddBarToKLine_NullInstrument() {
        BarDTO bar = new BarDTO(null, "1MIN", iBar);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
        mockedEncoder.verifyNoInteractions();
    }

    @Test
    public void testAddBarToKLine_NullPeriod() {
        BarDTO bar = new BarDTO("EUR/USD", null, iBar);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
        mockedEncoder.verifyNoInteractions();
    }

    @Test
    public void testAddBarToKLine_EncoderReturnsNull() {
        BarDTO bar = new BarDTO("EUR/USD", "1MIN", iBar);
        mockedEncoder.when(() -> MsgpackEncoder.encode(bar)).thenReturn(null);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
    }

    @Test
    public void testGetKLine_Success() {
        String instrument = "EUR/USD";
        String period = "1MIN";
        String expectedKey = "kline:EURUSD:1MIN";
        byte[] barData = "mocked-bar-data".getBytes();
        List<byte[]> barDataList = Collections.singletonList(barData);
        BarDTO expectedBar = new BarDTO(instrument, period, iBar);

        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        when(listOperationsBytes.range(expectedKey, 0, -1)).thenReturn(barDataList);
        mockedDecoder.when(() -> MsgpackDecoder.decode(barData, BarDTO.class)).thenReturn(expectedBar);

        List<BarDTO> result = redisService.getKLine(instrument, period);

        assertEquals(1, result.size());
        assertEquals(expectedBar, result.get(0));
    }

    @Test
    public void testGetKLine_EmptyList() {
        String instrument = "EUR/USD";
        String period = "1MIN";
        String expectedKey = "kline:EURUSD:1MIN";

        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        when(listOperationsBytes.range(expectedKey, 0, -1)).thenReturn(Collections.emptyList());

        List<BarDTO> result = redisService.getKLine(instrument, period);

        assertTrue(result.isEmpty());
        mockedDecoder.verifyNoInteractions();
    }
    //</editor-fold>

    //<editor-fold desc="Publish Tests">
    @Test
    public void testPublishTick() {
        TickDTO tick = new TickDTO("EUR/USD", 123L, 1.1, 1.2);
        byte[] tickData = "mocked-tick-data".getBytes();
        String expectedChannel = "tick:EURUSD";
        mockedEncoder.when(() -> MsgpackEncoder.encode(tick)).thenReturn(tickData);

        redisService.publishTick(tick);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, tickData);
    }

    @Test
    public void testPublishBar() {
        BarDTO bar = new BarDTO("GBP/USD", "5MINS", iBar);
        byte[] barData = "mocked-bar-data".getBytes();
        String expectedChannel = "kline:GBPUSD:5MINS";
        mockedEncoder.when(() -> MsgpackEncoder.encode(bar)).thenReturn(barData);

        redisService.publishBar(bar);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, barData);
    }

    @Test
    public void testPublishAccountStatus() {
        double balance = 10000.0;
        double equity = 10500.50;
        byte[] statusData = "mocked-status-data".getBytes();
        String expectedChannel = "account:status";
        ArgumentCaptor<AccountStatusDTO> captor = ArgumentCaptor.forClass(AccountStatusDTO.class);

        mockedEncoder.when(() -> MsgpackEncoder.encode(captor.capture())).thenReturn(statusData);

        redisService.publishAccountStatus(balance, equity);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, statusData);
        assertEquals(balance, captor.getValue().getBalance());
        assertEquals(equity, captor.getValue().getEquity());
    }

    @Test
    public void testPublishOrderEvent() {
        byte[] eventData = "mocked-event-data".getBytes();
        String expectedChannel = "order:event";
        ArgumentCaptor<OrderEventDTO> captor = ArgumentCaptor.forClass(OrderEventDTO.class);
        mockedEncoder.when(() -> MsgpackEncoder.encode(captor.capture())).thenReturn(eventData);

        when(iMessage.getType()).thenReturn(IMessage.Type.ORDER_SUBMIT_OK);
        when(iMessage.getCreationTime()).thenReturn(123L);
        when(iMessage.getReasons()).thenReturn(Collections.emptySet());
        when(iMessage.getOrder()).thenReturn(null);

        redisService.publishOrderEvent(iMessage);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, eventData);
        assertNotNull(captor.getValue());
    }

    @Test
    public void testPublishError() {
        String errorMessage = "This is an error";
        String expectedChannel = "gateway:error";
        redisService.publishError(errorMessage);
        verify(redisTemplateString).convertAndSend(expectedChannel, errorMessage);
    }

    @Test
    public void testPublishInfo() {
        String infoMessage = "This is an info message";
        String expectedChannel = "gateway:info";
        redisService.publishInfo(infoMessage);
        verify(redisTemplateString).convertAndSend(expectedChannel, infoMessage);
    }

    @Test
    public void testPublishGatewayStatus() {
        GatewayStatusDTO statusDTO = new GatewayStatusDTO("CONNECTED", "Gateway is up.");
        byte[] statusData = "mocked-status-data".getBytes();
        String expectedChannel = "gateway:status";
        mockedEncoder.when(() -> MsgpackEncoder.encode(statusDTO)).thenReturn(statusData);

        redisService.publishGatewayStatus(statusDTO);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, statusData);
    }

    @Test
    public void testPublishInstrumentInfo() {
        InstrumentInfoDTO infoDTO = new InstrumentInfoDTO();
        String requestId = "req-123";
        byte[] infoData = "mocked-info-data".getBytes();
        String expectedChannel = "info:instrument:response:req-123";
        mockedEncoder.when(() -> MsgpackEncoder.encode(infoDTO)).thenReturn(infoData);

        redisService.publishInstrumentInfo(infoDTO, requestId);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, infoData);
    }
    //</editor-fold>

}
