package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IBar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BarDTOTest {

    @Test
    public void testBarDTOConstructorAndGetters() {
        // 1. Setup mock IBar object
        IBar bar = mock(IBar.class);
        when(bar.getOpen()).thenReturn(1.0998);
        when(bar.getClose()).thenReturn(1.1048);
        when(bar.getLow()).thenReturn(1.0988);
        when(bar.getHigh()).thenReturn(1.1058);
        when(bar.getTime()).thenReturn(1672531200000L); // Example timestamp

        String instrument = "EUR/USD";
        String period = "M1";

        // 2. Create an instance of BarDTO
        BarDTO barDTO = new BarDTO(instrument, period, bar);

        // 3. Assert that the values are correctly transferred
        assertEquals(instrument, barDTO.getInstrument());
        assertEquals(period, barDTO.getPeriod());
        assertEquals(1672531200000L, barDTO.getTime());

        assertEquals(1.0998, barDTO.getOpen());
        assertEquals(1.1048, barDTO.getClose());
        assertEquals(1.0988, barDTO.getLow());
        assertEquals(1.1058, barDTO.getHigh());
    }
}
