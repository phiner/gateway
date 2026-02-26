package phiner.de5.net.gateway.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccountStatusDTOTest {

    @Test
    public void testConstructorAndGetters() {
        double balance = 10000.0;
        double equity = 12000.0;
        double margin = 50.0;
        double unrealizedPL = 2000.0;

        AccountStatusDTO accountStatusDTO = new AccountStatusDTO(balance, equity, margin, unrealizedPL);

        assertEquals(balance, accountStatusDTO.getBalance());
        assertEquals(equity, accountStatusDTO.getEquity());
        assertEquals(margin, accountStatusDTO.getMargin());
        assertEquals(unrealizedPL, accountStatusDTO.getUnrealizedPL());
    }
}
