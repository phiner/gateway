package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CloseMarketOrderRequest {

    private final String orderId;

    @JsonCreator
    public CloseMarketOrderRequest(@JsonProperty("orderId") String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
