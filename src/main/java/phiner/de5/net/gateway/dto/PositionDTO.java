package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.JFException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionDTO {
    private String dealId;
    private String dealReference;
    private String instrument;
    private String direction;
    private double amount;
    private double openPrice;
    private double profitLoss;

    public PositionDTO(IOrder order) throws JFException {
        this.dealId = order.getId();
        this.dealReference = order.getLabel();
        this.instrument = order.getInstrument().toString();
        this.direction = order.isLong() ? "BUY" : "SELL";
        this.amount = order.getAmount();
        this.openPrice = order.getOpenPrice();
        this.profitLoss = order.getProfitLossInAccountCurrency();
    }
}
