
package phiner.de5.net.gateway.config;

import com.dukascopy.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import phiner.de5.net.gateway.strategy.TradingStrategy;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ForexConfig implements ApplicationListener<ContextRefreshedEvent> {

    @Value("${gateway.kline.storage-limit}")
    private int klineStorageLimit;

    @Value("${forex.periods}")
    private String periodsValue;

    private final TradingStrategy tradingStrategy;

    public ForexConfig(TradingStrategy tradingStrategy) {
        this.tradingStrategy = tradingStrategy;
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        IContext context = tradingStrategy.getContext();
        if (context == null) {
            System.err.println("IContext not initialized in TradingStrategy. Cannot preload historical data.");
            return;
        }

        Set<Period> periods = Arrays.stream(periodsValue.split(","))
                .map(Period::valueOf)
                .collect(Collectors.toSet());

        IHistory history = context.getHistory();

        try {
            Set<Instrument> subscribedInstruments = context.getSubscribedInstruments();
            System.out.println("Subscribed instruments: " + subscribedInstruments);

            for (Instrument instrument : subscribedInstruments) {
                for (Period period : periods) {
                    System.out.println("Preloading historical data for " + instrument.toString() + " and period " + period);
                    history.getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, klineStorageLimit, context.getTime(), 0);
                }
            }
        } catch (JFException e) {
            System.err.println("Failed to preload historical data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
