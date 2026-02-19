package phiner.de5.net.gateway.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TickDTOTest {

    @Test
    public void testConstructorAndGetters() {
        String instrument = "EUR/USD";
        long time = System.currentTimeMillis();
        double ask = 1.12345;
        double bid = 1.12340;

        TickDTO tickDTO = new TickDTO(instrument, time, ask, bid);

        assertEquals(instrument, tickDTO.getInstrument());
        assertEquals(time, tickDTO.getTime());
        assertEquals(ask, tickDTO.getAsk());
        assertEquals(bid, tickDTO.getBid());
    }
}
