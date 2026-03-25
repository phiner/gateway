package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubmitOrderRequest {
    @JsonProperty("requestId")
    private String requestId;
    @JsonProperty("instrument")
    private String instrument;
    @JsonProperty("orderType")
    private String orderType;
    @JsonProperty("orderCommand")
    private String orderCommand;
    @JsonProperty("amount")
    private Double amount;
    @JsonProperty("price")
    private Double price;
    @JsonProperty("label") // Added field
    private String label;
    @JsonProperty("slippage")
    private Double slippage;
    @JsonProperty("stopLossPrice")
    private Double stopLossPrice;
    @JsonProperty("takeProfitPrice")
    private Double takeProfitPrice;
    @JsonProperty("comments")
    private String comments;
}
