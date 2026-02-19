package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class BarDTOTest {

    @Mock
    private IBar iBar;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(iBar.getTime()).thenReturn(123456789L);
        when(iBar.getOpen()).thenReturn(1.1);
        when(iBar.getClose()).thenReturn(1.2);
        when(iBar.getLow()).thenReturn(1.0);
        when(iBar.getHigh()).thenReturn(1.3);
    }

    @Test
    public void testConstructorAndGetters() {
        String instrument = "EUR/USD";
        String period = "1min";

        BarDTO barDTO = new BarDTO(instrument, period, iBar);

        assertEquals(instrument, barDTO.getInstrument());
        assertEquals(period, barDTO.getPeriod());
        assertEquals(123456789L, barDTO.getTime());
        assertEquals(1.1, barDTO.getOpen());
        assertEquals(1.2, barDTO.getClose());
        assertEquals(1.0, barDTO.getLow());
        assertEquals(1.3, barDTO.getHigh());
    }
}
