package phiner.de5.net.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountStatusDTO {
    private final double balance;
    private final double equity;
    private final double margin;
    private final double unrealizedPL;

    public AccountStatusDTO(
            @JsonProperty("balance") double balance,
            @JsonProperty("equity") double equity,
            @JsonProperty("margin") double margin,
            @JsonProperty("unrealizedPL") double unrealizedPL) {
        this.balance = balance;
        this.equity = equity;
        this.margin = margin;
        this.unrealizedPL = unrealizedPL;
    }

    @JsonProperty("balance")
    public double getBalance() {
        return balance;
    }

    @JsonProperty("equity")
    public double getEquity() {
        return equity;
    }

    @JsonProperty("margin")
    public double getMargin() {
        return margin;
    }

    @JsonProperty("unrealizedPL")
    public double getUnrealizedPL() {
        return unrealizedPL;
    }
}
