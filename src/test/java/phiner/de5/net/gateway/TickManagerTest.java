package phiner.de5.net.gateway;

import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import phiner.de5.net.gateway.dto.TickDTO;
import phiner.de5.net.gateway.service.RedisService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TickManagerTest {

    @Mock
    private RedisService redisService;

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

        when(tick.getTime()).thenReturn(currentTime);
        when(tick.getAsk()).thenReturn(ask);
        when(tick.getBid()).thenReturn(bid);

        // When
        tickManager.onTick(instrument.toString(), tick);

        // Then
        ArgumentCaptor<TickDTO> tickCaptor = ArgumentCaptor.forClass(TickDTO.class);
        verify(redisService).publishTick(tickCaptor.capture());

        TickDTO capturedTick = tickCaptor.getValue();
        assertEquals(instrument.toString(), capturedTick.getInstrument());
        assertEquals(currentTime, capturedTick.getTime());
        assertEquals(ask, capturedTick.getAsk());
        assertEquals(bid, capturedTick.getBid());
    }

}
