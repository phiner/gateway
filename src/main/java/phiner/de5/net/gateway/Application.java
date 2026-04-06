package phiner.de5.net.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = { "phiner.de5.net.gateway" })
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Exception e) {
            System.err.println("CRITICAL: Application failed to start.");
            e.printStackTrace();
        }
    }

    @Bean
    public CommandLineRunner diagnosticRunner(@Value("${spring.data.redis.host:localhost}") String redisHost) {
        return args -> {
            log.info("==================================================");
            log.info("GATEWAY STARTUP DIAGNOSTICS:");
            log.info("Active Redis Host (from .env/props): {}", redisHost);
            log.info("==================================================");
        };
    }
}
