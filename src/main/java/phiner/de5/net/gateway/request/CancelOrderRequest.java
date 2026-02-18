package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CancelOrderRequest {
    @JsonProperty("requestId")
    private String requestId;
    @JsonProperty("orderId")
    private String orderId;
}
