package phiner.de5.net.gateway;

import com.dukascopy.api.IBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import phiner.de5.net.gateway.dto.BarDTO;
import phiner.de5.net.gateway.service.RedisService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KLineManagerTest {

    @Mock
    private RedisService redisService;

    @Mock
    private IBar iBar;

    @InjectMocks
    private KLineManager kLineManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Common mock behavior can be set up here
        when(iBar.getTime()).thenReturn(123456789L);
        when(iBar.getOpen()).thenReturn(1.1);
        when(iBar.getClose()).thenReturn(1.2);
        when(iBar.getLow()).thenReturn(1.0);
        when(iBar.getHigh()).thenReturn(1.3);
    }

    @Test
    public void testOnBar() {
        // Given
        String instrument = "EUR/USD";
        String period = "1min";
        BarDTO bar = new BarDTO(instrument, period, iBar);

        // When
        kLineManager.onBar(instrument, bar);

        // Then
        assertEquals(bar, kLineManager.getLastBar(instrument));
        verify(redisService).addBarToKLine(bar);
        verify(redisService).publishBar(bar);
    }

    @Test
    public void testGetSubscribedInstruments() {
        // Given
        String instrument1 = "EUR/USD";
        String instrument2 = "USD/JPY";
        String period = "1min";

        // Reuse the same mocked iBar for creating different BarDTOs
        BarDTO bar1 = new BarDTO(instrument1, period, iBar);
        BarDTO bar2 = new BarDTO(instrument2, period, iBar);

        kLineManager.onBar(instrument1, bar1);
        kLineManager.onBar(instrument2, bar2);

        // When
        Set<String> instruments = kLineManager.getSubscribedInstruments();

        // Then
        assertEquals(2, instruments.size());
        assertTrue(instruments.contains(instrument1));
        assertTrue(instruments.contains(instrument2));
    }

    @Test
    public void testGetLastBar() {
        // Given
        String instrument = "EUR/USD";
        String period = "1min";
        BarDTO bar = new BarDTO(instrument, period, iBar);
        kLineManager.onBar(instrument, bar);

        // When
        BarDTO lastBar = kLineManager.getLastBar(instrument);

        // Then
        assertNotNull(lastBar);
        assertEquals(bar, lastBar);
    }
}
