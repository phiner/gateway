package phiner.de5.net.gateway;

import com.dukascopy.api.system.ClientFactory;
import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import phiner.de5.net.gateway.strategy.TradingStrategy;

@SpringBootApplication
@ComponentScan(basePackages = { "phiner.de5.net.gateway", "phiner.de5.net.gateway.config",
        "phiner.de5.net.gateway.strategy", "phiner.de5.net.gateway.listener" })
public class Application {

    @Value("${JFOREX_URL}")
    private String jnlpUrl;

    @Value("${JFOREX_USERNAME}")
    private String username;

    @Value("${JFOREX_PASSWORD}")
    private String password;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ConfigurableApplicationContext context) {
        return args -> {
            // --- JForex Client Setup ---
            final IClient client = ClientFactory.getDefaultInstance();
            client.setSystemListener(new ISystemListener() {
                private int lightReconnects = 3;

                @Override
                public void onStart(long processId) {
                    System.out.println("Strategy started: " + processId);
                }

                @Override
                public void onStop(long processId) {
                    System.out.println("Strategy stopped: " + processId);
                    if (client.getStartedStrategies().isEmpty()) {
                        System.exit(0);
                    }
                }

                @Override
                public void onConnect() {
                    System.out.println("Connected to JForex server");
                    lightReconnects = 3;
                }

                @Override
                public void onDisconnect() {
                    System.out.println("Disconnected from JForex server");
                    if (lightReconnects > 0) {
                        System.out.println("Attempting to reconnect...");
                        try {
                            client.reconnect();
                        } catch (Exception e) {
                            System.err.println("Reconnect failed: " + e.getMessage());
                        }
                        lightReconnects--;
                    } else {
                        System.out.println("Exceeded reconnect attempts, stopping client.");
                        System.exit(1);
                    }
                }
            });

            System.out.println("Connecting to JForex platform...");
            client.connect(jnlpUrl, username, password);

            // Wait for connection
            int i = 10; // wait max 10 seconds
            while (i > 0 && !client.isConnected()) {
                Thread.sleep(1000);
                i--;
            }

            if (!client.isConnected()) {
                System.err.println("Failed to connect to JForex platform.");
                System.exit(1);
            }

            // Start the TradingStrategy strategy
            System.out.println("Starting TradingStrategy strategy...");
            TradingStrategy tradingStrategy = context.getBean(TradingStrategy.class);
            client.startStrategy(tradingStrategy);
        };
    }
}
