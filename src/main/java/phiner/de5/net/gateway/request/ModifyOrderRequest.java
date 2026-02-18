package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ModifyOrderRequest {
    @JsonProperty("requestId")
    private String requestId;
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("stopLossPrice")
    private double stopLossPrice;
    @JsonProperty("takeProfitPrice")
    private double takeProfitPrice;
}
