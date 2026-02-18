package phiner.de5.net.gateway.dto;

import com.dukascopy.api.ITick;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TickDTOTest {

    @Test
    public void testTickDTOConstructorAndGetters() {
        // 1. Setup mock ITick object
        ITick tick = mock(ITick.class);
        when(tick.getAsk()).thenReturn(1.23456);
        when(tick.getBid()).thenReturn(1.23450);
        when(tick.getTime()).thenReturn(1672531200123L); // Example timestamp

        String instrument = "GBP/USD";

        // 2. Create an instance of TickDTO using the constructor
        TickDTO tickDTO = new TickDTO(
                instrument,
                tick.getTime(),
                tick.getAsk(),
                tick.getBid()
        );

        // 3. Assert that the values are correctly transferred
        assertEquals(instrument, tickDTO.getInstrument());
        assertEquals(1672531200123L, tickDTO.getTime());
        assertEquals(1.23456, tickDTO.getAsk());
        assertEquals(1.23450, tickDTO.getBid());
    }
}
