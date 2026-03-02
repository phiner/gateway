package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class PositionDTOTest {

    @Mock
    private IOrder order;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testConstructorFromOrder() throws JFException {
        when(order.getId()).thenReturn("pos-123");
        when(order.getLabel()).thenReturn("label-123");
        when(order.getInstrument()).thenReturn(Instrument.EURUSD);
        when(order.isLong()).thenReturn(true);
        when(order.getAmount()).thenReturn(0.01);
        when(order.getOpenPrice()).thenReturn(1.0850);
        when(order.getStopLossPrice()).thenReturn(1.0800);
        when(order.getTakeProfitPrice()).thenReturn(1.0900);
        when(order.getProfitLossInAccountCurrency()).thenReturn(15.5);
        when(order.getCommission()).thenReturn(1.23);

        PositionDTO dto = new PositionDTO(order);

        assertEquals("pos-123", dto.getDealId());
        assertEquals("label-123", dto.getDealReference());
        assertEquals("EUR/USD", dto.getInstrument());
        assertEquals("BUY", dto.getDirection());
        assertEquals(0.01, dto.getAmount());
        assertEquals(1.0850, dto.getOpenPrice());
        assertEquals(1.0800, dto.getStopLossPrice());
        assertEquals(1.0900, dto.getTakeProfitPrice());
        assertEquals(15.5, dto.getProfitLoss());
        assertEquals(1.23, dto.getCommission());
    }
}
