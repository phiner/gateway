package phiner.de5.net.gateway.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstrumentInfoDTOTest {

    @Test
    public void testConstructorAndGetters() {
        String name = "EUR/USD";
        String currency = "USD";
        double pip = 0.0001;
        double point = 0.00001;
        String description = "Euro vs US Dollar";

        InstrumentInfoDTO instrumentInfoDTO = new InstrumentInfoDTO(name, currency, pip, point, description);

        assertEquals(name, instrumentInfoDTO.getName());
        assertEquals(currency, instrumentInfoDTO.getCurrency());
        assertEquals(pip, instrumentInfoDTO.getPip());
        assertEquals(point, instrumentInfoDTO.getPoint());
        assertEquals(description, instrumentInfoDTO.getDescription());
    }
}
