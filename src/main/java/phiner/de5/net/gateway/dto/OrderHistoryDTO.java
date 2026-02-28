package phiner.de5.net.gateway.dto;

import com.dukascopy.api.IOrder;
import com.dukascopy.api.JFException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryDTO {
    private String dealId;
    private String label;
    private String instrument;
    private String direction;
    private double amount;
    private String state;
    private double openPrice;
    private double closePrice;
    private long creationTime;
    private long fillTime;
    private long closeTime;
    private double pips;
    private double profitLoss;

    public OrderHistoryDTO(IOrder order) throws JFException {
        this.dealId = order.getId();
        this.label = order.getLabel();
        this.instrument = order.getInstrument().toString();
        this.direction = order.isLong() ? "BUY" : "SELL";
        this.amount = order.getAmount();
        this.state = order.getState().toString();
        this.openPrice = order.getOpenPrice();
        this.closePrice = order.getClosePrice();
        this.creationTime = order.getCreationTime();
        this.fillTime = order.getFillTime();
        this.closeTime = order.getCloseTime();
        this.pips = order.getProfitLossInPips();
        this.profitLoss = order.getProfitLossInAccountCurrency();
    }
}
