package phiner.de5.net.gateway.dto;

public class AccountStatusDTO {
    private final double balance;
    private final double equity;

    public AccountStatusDTO(double balance, double equity) {
        this.balance = balance;
        this.equity = equity;
    }

    public double getBalance() {
        return balance;
    }

    public double getEquity() {
        return equity;
    }
}
