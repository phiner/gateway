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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import phiner.de5.net.gateway.MsgpackUtil;
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
    private SetOperations<String, String> setOperationsString;

    @Mock
    private HashOperations<String, Object, Object> hashOperationsBytes;

    @Mock
    private IBar iBar;

    @Mock
    private IMessage iMessage;

    private RedisService redisService;

    private MockedStatic<MsgpackUtil> mockedUtil;

    @BeforeEach
    public void setUp() {
        redisService = new RedisService(redisTemplateBytes, redisTemplateString);
        ReflectionTestUtils.setField(redisService, "klineStorageLimit", 100);
        mockedUtil = Mockito.mockStatic(MsgpackUtil.class);
    }

    @AfterEach
    public void tearDown() {
        mockedUtil.close();
    }

    @Test
    public void testAddBarToKLine_Success() {
        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        BarDTO bar = new BarDTO("EUR/USD", "ONE_MIN", iBar);
        byte[] barData = "mocked-bar-data".getBytes();
        String expectedKey = "gateway:kline:EUR/USD:1m";
        mockedUtil.when(() -> MsgpackUtil.encode(bar)).thenReturn(barData);

        redisService.addBarToKLine(bar);

        verify(listOperationsBytes).leftPush(expectedKey, barData);
        verify(listOperationsBytes).trim(expectedKey, 0, 99);
    }

    @Test
    public void testAddBarToKLine_NullInstrument() {
        BarDTO bar = new BarDTO(null, "ONE_MIN", iBar);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
        mockedUtil.verifyNoInteractions();
    }

    @Test
    public void testAddBarToKLine_NullPeriod() {
        BarDTO bar = new BarDTO("EUR/USD", null, iBar);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
        mockedUtil.verifyNoInteractions();
    }

    @Test
    public void testAddBarToKLine_EncoderReturnsNull() {
        BarDTO bar = new BarDTO("EUR/USD", "ONE_MIN", iBar);
        mockedUtil.when(() -> MsgpackUtil.encode(bar)).thenReturn(null);
        redisService.addBarToKLine(bar);
        verifyNoInteractions(redisTemplateBytes);
    }

    @Test
    public void testGetKLine_Success() {
        String instrument = "EUR/USD";
        String period = "1m";
        String expectedKey = "gateway:kline:EUR/USD:1m";
        byte[] barData = "mocked-bar-data".getBytes();
        List<byte[]> barDataList = Collections.singletonList(barData);
        BarDTO expectedBar = new BarDTO(instrument, period, iBar);

        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        when(listOperationsBytes.range(expectedKey, 0, -1)).thenReturn(barDataList);
        mockedUtil.when(() -> MsgpackUtil.decode(barData, BarDTO.class)).thenReturn(expectedBar);

        List<BarDTO> result = redisService.getKLine(instrument, period);

        assertEquals(1, result.size());
        assertEquals(expectedBar, result.get(0));
    }

    @Test
    public void testGetKLine_EmptyList() {
        String instrument = "EUR/USD";
        String period = "1m";
        String expectedKey = "gateway:kline:EUR/USD:1m";

        when(redisTemplateBytes.opsForList()).thenReturn(listOperationsBytes);
        when(listOperationsBytes.range(expectedKey, 0, -1)).thenReturn(Collections.emptyList());

        List<BarDTO> result = redisService.getKLine(instrument, period);

        assertTrue(result.isEmpty());
        mockedUtil.verifyNoInteractions();
    }

    @Test
    public void testPublishTick() {
        TickDTO tick = new TickDTO("EUR/USD", 123L, 1.1, 1.2);
        byte[] tickData = "mocked-tick-data".getBytes();
        String expectedChannel = "gateway:tick:EUR/USD";
        mockedUtil.when(() -> MsgpackUtil.encode(tick)).thenReturn(tickData);

        redisService.publishTick(tick);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, tickData);
    }

    @Test
    public void testPublishBar() {
        BarDTO bar = new BarDTO("GBP/USD", "5 Mins", iBar);
        byte[] barData = "mocked-bar-data".getBytes();
        String expectedChannel = "gateway:kline:GBP/USD:5m";
        mockedUtil.when(() -> MsgpackUtil.encode(bar)).thenReturn(barData);

        redisService.publishBar(bar);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, barData);
    }

    @Test
    public void testPublishAccountStatus() {
        double balance = 10000.0;
        double equity = 10500.50;
        double baseEquity = 10005.0;
        double margin = 50.0;
        double unrealizedPL = 495.50;
        byte[] statusData = "mocked-status-data".getBytes();
        String expectedChannel = "gateway:account:status";
        ArgumentCaptor<AccountStatusDTO> captor = ArgumentCaptor.forClass(AccountStatusDTO.class);

        mockedUtil.when(() -> MsgpackUtil.encode(captor.capture())).thenReturn(statusData);

        redisService.publishAccountStatus(balance, equity, baseEquity, margin, unrealizedPL);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, statusData);
        assertEquals(balance, captor.getValue().getBalance());
        assertEquals(equity, captor.getValue().getEquity());
        assertEquals(baseEquity, captor.getValue().getBaseEquity());
        assertEquals(margin, captor.getValue().getMargin());
        assertEquals(unrealizedPL, captor.getValue().getUnrealizedPL());
    }

    @Test
    public void testPublishOrderEvent() {
        byte[] eventData = "mocked-event-data".getBytes();
        String expectedChannel = "gateway:order:event";
        ArgumentCaptor<OrderEventDTO> captor = ArgumentCaptor.forClass(OrderEventDTO.class);
        mockedUtil.when(() -> MsgpackUtil.encode(captor.capture())).thenReturn(eventData);

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
        mockedUtil.when(() -> MsgpackUtil.encode(statusDTO)).thenReturn(statusData);

        redisService.publishGatewayStatus(statusDTO);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, statusData);
    }

    @Test
    public void testPublishInstrumentInfo() {
        InstrumentInfoDTO infoDTO = new InstrumentInfoDTO();
        String requestId = "req-123";
        byte[] infoData = "mocked-info-data".getBytes();
        String expectedChannel = "gateway:info:instrument:response:req-123";
        mockedUtil.when(() -> MsgpackUtil.encode(infoDTO)).thenReturn(infoData);

        redisService.publishInstrumentInfo(infoDTO, requestId);

        verify(redisTemplateBytes).convertAndSend(expectedChannel, infoData);
    }

    @Test
    public void testSaveConfigInstruments() {
        List<String> instruments = List.of("EUR/USD", "GBP/USD");
        String key = "gateway:config:instruments";
        when(redisTemplateString.opsForSet()).thenReturn(setOperationsString);

        redisService.saveConfigInstruments(instruments);

        verify(redisTemplateString).delete(key);
        verify(setOperationsString).add(key, instruments.toArray(new String[0]));
    }

    @Test
    public void testSaveConfigPeriods() {
        List<String> periods = List.of("FIVE_MINS", "DAILY");
        String key = "gateway:config:periods";
        when(redisTemplateString.opsForSet()).thenReturn(setOperationsString);

        redisService.saveConfigPeriods(periods);

        verify(redisTemplateString).delete(key);
        verify(setOperationsString).add(key, "5m", "1d");
    }

    @Test
    public void testRefreshPositionsHash_Success() {
        PositionDTO pos = new PositionDTO("deal1", "ref1", "EUR/USD", "BUY", 0.1, 1.1, 1.0, 1.2, 10.0, 1.5);
        List<PositionDTO> positions = List.of(pos);
        byte[] posData = "mocked-pos-data".getBytes();
        String hashKey = "gateway:positions:active";

        when(redisTemplateBytes.opsForHash()).thenReturn(hashOperationsBytes);
        mockedUtil.when(() -> MsgpackUtil.encode(pos)).thenReturn(posData);

        redisService.refreshPositionsHash(positions);

        verify(redisTemplateBytes).delete(hashKey);
        verify(hashOperationsBytes).put(hashKey, "deal1", posData);
        verify(redisTemplateString).convertAndSend(eq("gateway:positions:updated"), anyString());
    }

    @Test
    public void testNotifyPositionsUpdated() {
        redisService.notifyPositionsUpdated();
        verify(redisTemplateString).convertAndSend(eq("gateway:positions:updated"), anyString());
    }

    @Test
    public void testUpdateHistoryHash_Success() {
        OrderHistoryDTO order = new OrderHistoryDTO();
        order.setDealId("deal-hist-1");
        List<OrderHistoryDTO> orders = List.of(order);
        byte[] orderData = "mocked-order-data".getBytes();
        String hashKey = "gateway:orders:history";

        when(redisTemplateBytes.opsForHash()).thenReturn(hashOperationsBytes);
        mockedUtil.when(() -> MsgpackUtil.encode(order)).thenReturn(orderData);

        redisService.updateHistoryHash(orders);

        ArgumentCaptor<java.util.Map<String, byte[]>> mapCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(hashOperationsBytes).putAll(eq(hashKey), mapCaptor.capture());
        assertEquals(1, mapCaptor.getValue().size());
        assertArrayEquals(orderData, mapCaptor.getValue().get("deal-hist-1"));
    }

    @Test
    public void testNotifyHistoryUpdated() {
        String instrument = "EUR/USD";
        redisService.notifyHistoryUpdated(instrument);
        verify(redisTemplateString).convertAndSend("gateway:orders:history:updated", instrument);
    }
}
