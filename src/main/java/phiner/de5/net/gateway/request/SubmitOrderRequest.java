package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
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
    private double amount;
    @JsonProperty("price")
    private double price;
    @JsonProperty("label") // Added field
    private String label;
    @JsonProperty("stopLossPrice")
    private double stopLossPrice;
    @JsonProperty("takeProfitPrice")
    private double takeProfitPrice;
}
