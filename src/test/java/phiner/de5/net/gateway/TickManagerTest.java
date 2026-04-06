package phiner.de5.net.gateway;

import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.service.ForexTickProducer;
import phiner.de5.net.gateway.service.RedisService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TickManagerTest {

    @Mock
    private RedisService redisService;

    @Mock
    private ForexTickProducer forexTickProducer;

    @Mock
    private ITick tick;

    @InjectMocks
    private TickManager tickManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @SuppressWarnings("null")
    public void testOnTick() {
        // Given
        Instrument instrument = Instrument.EURUSD;
        long currentTime = System.currentTimeMillis();
        double ask = 1.12345;
        double bid = 1.12340;
        double askVol = 1.5;
        double bidVol = 2.0;

        when(tick.getTime()).thenReturn(currentTime);
        when(tick.getAsk()).thenReturn(ask);
        when(tick.getBid()).thenReturn(bid);
        when(tick.getAskVolume()).thenReturn(askVol);
        when(tick.getBidVolume()).thenReturn(bidVol);

        // When
        tickManager.onTick(instrument.toString(), tick);

        // Then
        // 验证 ForexTickProducer 是否被正确调用
        verify(forexTickProducer).sendTickAsync(
                instrument.toString(),
                currentTime,
                bid,
                ask,
                bidVol,
                askVol
        );

        // 验证内存缓存是否更新
        TickDTO lastTick = tickManager.getLastTick(instrument.toString());
        assertEquals(currentTime, lastTick.getTime());
        assertEquals(ask, lastTick.getAsk());
        assertEquals(bid, lastTick.getBid());
    }

}
