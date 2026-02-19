package phiner.de5.net.gateway.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GatewayStatusDTOTest {

    @Test
    public void testConstructorAndGetters() {
        String status = "Connected";
        String message = "Gateway is connected to the broker.";

        GatewayStatusDTO gatewayStatusDTO = new GatewayStatusDTO(status, message);

        assertEquals("gateway", gatewayStatusDTO.getService());
        assertEquals(status, gatewayStatusDTO.getStatus());
        assertEquals(message, gatewayStatusDTO.getMessage());
        assertNotNull(gatewayStatusDTO.getTimestamp());
    }
}
