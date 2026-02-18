package phiner.de5.net.gateway.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenMarketOrderRequest {

    private final String instrument;
    private final double amount;
    private final MarketOrderType orderType;
    private final String label; // Added field
    private final Double slippage;
    private final Double stopLossPrice;
    private final Double takeProfitPrice;

    @JsonCreator
    public OpenMarketOrderRequest(
            @JsonProperty("instrument") String instrument,
            @JsonProperty("amount") double amount,
            @JsonProperty("orderType") MarketOrderType orderType,
            @JsonProperty("label") String label, // Added field
            @JsonProperty("slippage") Double slippage,
            @JsonProperty("stopLossPrice") Double stopLossPrice,
            @JsonProperty("takeProfitPrice") Double takeProfitPrice) {
        this.instrument = instrument;
        this.amount = amount;
        this.orderType = orderType;
        this.label = label; // Added field
        this.slippage = slippage;
        this.stopLossPrice = stopLossPrice;
        this.takeProfitPrice = takeProfitPrice;
    }

    // Getters
    public String getInstrument() {
        return instrument;
    }

    public double getAmount() {
        return amount;
    }

    public MarketOrderType getOrderType() {
        return orderType;
    }

    public String getLabel() {
        return label;
    }

    public Double getSlippage() {
        return slippage;
    }

    public Double getStopLossPrice() {
        return stopLossPrice;
    }

    public Double getTakeProfitPrice() {
        return takeProfitPrice;
    }
}
