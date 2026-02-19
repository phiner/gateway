package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IEngine;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class OrderEventDTOTest {

    @Mock
    private IMessage message;
    @Mock
    private IOrder order;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(message.getOrder()).thenReturn(order);
        when(message.getType()).thenReturn(IMessage.Type.ORDER_FILL_OK);
        when(message.getCreationTime()).thenReturn(123456789L);
        when(message.getReasons()).thenReturn(Collections.emptySet());

        when(order.getId()).thenReturn("test-order-id");
        when(order.getLabel()).thenReturn("test-label");
        when(order.getInstrument()).thenReturn(Instrument.EURUSD);
        when(order.getState()).thenReturn(IOrder.State.FILLED);
        // Correctly use IEngine.OrderCommand.BUY for mocking
        when(order.getOrderCommand()).thenReturn(IEngine.OrderCommand.BUY);
        when(order.getAmount()).thenReturn(1.0);
        when(order.getOpenPrice()).thenReturn(1.2);
        when(order.getFillTime()).thenReturn(987654321L);
        when(order.getClosePrice()).thenReturn(1.3);
        when(order.getCloseTime()).thenReturn(111222333L);
    }

    @Test
    public void testConstructorAndGetters() {
        OrderEventDTO dto = new OrderEventDTO(message);

        assertEquals("test-order-id", dto.getMessageId());
        assertEquals("ORDER_FILL_OK", dto.getEventType());
        assertEquals(123456789L, dto.getCreationTime());
        assertEquals(null, dto.getReason());
        assertEquals("test-label", dto.getOrderLabel());
        assertEquals("EURUSD", dto.getInstrument());
        assertEquals("FILLED", dto.getOrderState());
        assertEquals("BUY", dto.getOrderCommand());
        assertEquals(1.0, dto.getAmount());
        assertEquals(1.2, dto.getOpenPrice());
        assertEquals(987654321L, dto.getFillTime());
        assertEquals(1.3, dto.getClosePrice());
        assertEquals(111222333L, dto.getCloseTime());
    }
}
