package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IEngine;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventDTOTest {

    @Mock
    private IMessage mockMessage;

    @Mock
    private IOrder mockOrder;

    private final long creationTime = 1678886401000L;
    private final String messageContent = "Order filled";

    @BeforeEach
    void setUp() {
        // Common message configuration
        when(mockMessage.getType()).thenReturn(IMessage.Type.ORDER_FILL_OK);
        when(mockMessage.getCreationTime()).thenReturn(creationTime);
        when(mockMessage.getReasons()).thenReturn(Collections.emptySet());
    }

    @Test
    void testDtoCreation_With_AssociatedOrder() {
        // Arrange: Configure mocks for a message that HAS an order
        when(mockOrder.getId()).thenReturn("order-id-123");
        when(mockOrder.getLabel()).thenReturn("test_order_label");
        when(mockOrder.getInstrument()).thenReturn(Instrument.EURUSD);
        when(mockOrder.getState()).thenReturn(IOrder.State.FILLED);
        when(mockOrder.getOrderCommand()).thenReturn(IEngine.OrderCommand.BUY);
        when(mockOrder.getAmount()).thenReturn(0.01);
        when(mockOrder.getOpenPrice()).thenReturn(1.12345);
        when(mockOrder.getFillTime()).thenReturn(1678886400000L);
        when(mockOrder.getClosePrice()).thenReturn(0.0); // Represents not closed
        when(mockOrder.getCloseTime()).thenReturn(-1L);    // Represents not closed

        when(mockMessage.getOrder()).thenReturn(mockOrder);

        // Act: Create the DTO from the mock message
        OrderEventDTO dto = new OrderEventDTO(mockMessage);

        // Assert: Verify all fields are correctly mapped
        assertEquals("order-id-123", dto.getMessageId()); // Should use Order ID
        assertEquals("ORDER_FILL_OK", dto.getEventType());
        assertEquals(creationTime, dto.getCreationTime());
        assertNull(dto.getReason());

        // Assert order-specific fields
        assertEquals("test_order_label", dto.getOrderLabel());
        assertEquals("EURUSD", dto.getInstrument());
        assertEquals("FILLED", dto.getOrderState());
        assertEquals("BUY", dto.getOrderCommand());
        assertEquals(0.01, dto.getAmount());
        assertEquals(1.12345, dto.getOpenPrice());
        assertEquals(1678886400000L, dto.getFillTime());
        assertEquals(0.0, dto.getClosePrice()); // DTO mirrors the value directly
        assertEquals(-1L, dto.getCloseTime());      // DTO mirrors the value directly
    }

    @Test
    void testDtoCreation_Without_AssociatedOrder() {
        // Arrange: Configure message to have NO associated order
        when(mockMessage.getOrder()).thenReturn(null);
        when(mockMessage.getContent()).thenReturn(messageContent);

        // Act: Create DTO
        OrderEventDTO dto = new OrderEventDTO(mockMessage);

        // Assert: Verify messageId uses the fallback logic
        String expectedMessageId = "msg_" + creationTime + "_" + messageContent.hashCode();
        assertEquals(expectedMessageId, dto.getMessageId());
        assertEquals("ORDER_FILL_OK", dto.getEventType());

        // Assert order fields are null/default
        assertNull(dto.getOrderLabel());
        assertNull(dto.getInstrument());
        assertEquals(0, dto.getAmount());
    }

    @Test
    void testDtoHandlesRejectionReason() {
        // Arrange: Configure message with a rejection reason and no order
        when(mockMessage.getReasons()).thenReturn(Collections.singleton(IMessage.Reason.UNDEFINED));
        when(mockMessage.getOrder()).thenReturn(null);
        when(mockMessage.getContent()).thenReturn(messageContent);

        // Act
        OrderEventDTO dto = new OrderEventDTO(mockMessage);

        // Assert
        assertEquals("UNDEFINED", dto.getReason());
    }
}
