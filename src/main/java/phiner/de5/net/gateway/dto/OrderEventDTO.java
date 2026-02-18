package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import java.io.Serializable;
import java.util.stream.Collectors;

/**
 * A Data Transfer Object (DTO) representing a simplified view of an
 * order-related event.
 * This class is designed to be serializable and sent over a network (e.g., via
 * Redis Pub/Sub).
 */
public class OrderEventDTO implements Serializable {

    private static final long serialVersionUID = 1L; // Recommended for Serializable classes

    // Fields from IMessage
    private final String messageId;
    private final String eventType;
    private final long creationTime;
    private final String reason; // e.g., for rejections

    // Fields from IOrder
    private final String orderLabel;
    private final String instrument;
    private final String orderState;
    private final String orderCommand;
    private final double amount;
    private final double openPrice;
    private final Long fillTime;
    private final Double closePrice;
    private final Long closeTime;

    public OrderEventDTO(IMessage message) {
        IOrder order = message.getOrder();

        if (order != null) {
            this.messageId = order.getId();
        } else {
            // Handle cases where there's no order attached to the message
            String content = message.getContent();
            this.messageId = "msg_" + message.getCreationTime() + "_" + (content != null ? content.hashCode() : "no_content");
        }

        this.eventType = message.getType().toString();
        this.creationTime = message.getCreationTime();

        if (!message.getReasons().isEmpty()) {
            this.reason = message.getReasons().stream()
                    .map(Enum::toString)
                    .collect(Collectors.joining(", "));
        } else {
            this.reason = null;
        }

        if (order != null) {
            this.orderLabel = order.getLabel();
            this.instrument = order.getInstrument().name();
            this.orderState = order.getState().toString();
            this.orderCommand = order.getOrderCommand().toString();
            this.amount = order.getAmount();
            this.openPrice = order.getOpenPrice();
            this.fillTime = order.getFillTime();
            this.closePrice = order.getClosePrice();
            this.closeTime = order.getCloseTime();
        } else {
            this.orderLabel = null;
            this.instrument = null;
            this.orderState = null;
            this.orderCommand = null;
            this.amount = 0;
            this.openPrice = 0;
            this.fillTime = null;
            this.closePrice = null;
            this.closeTime = null;
        }
    }

    // --- Getters ---

    public String getMessageId() {
        return messageId;
    }

    public String getEventType() {
        return eventType;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getReason() {
        return reason;
    }

    public String getOrderLabel() {
        return orderLabel;
    }

    public String getInstrument() {
        return instrument;
    }

    public String getOrderState() {
        return orderState;
    }

    public String getOrderCommand() {
        return orderCommand;
    }

    public double getAmount() {
        return amount;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public Long getFillTime() {
        return fillTime;
    }

    public Double getClosePrice() {
        return closePrice;
    }

    public Long getCloseTime() {
        return closeTime;
    }

    @Override
    public String toString() {
        return "OrderEventDTO{" +
                "messageId='" + messageId + "'" +
                ", eventType='" + eventType + "'" +
                ", creationTime=" + creationTime +
                ", reason='" + reason + "'" +
                ", orderLabel='" + orderLabel + "'" +
                ", instrument='" + instrument + "'" +
                ", orderState='" + orderState + "'" +
                ", orderCommand='" + orderCommand + "'" +
                ", amount=" + amount +
                ", openPrice=" + openPrice +
                ", fillTime=" + fillTime +
                ", closePrice=" + closePrice +
                ", closeTime=" + closeTime +
                '}';
    }
}
