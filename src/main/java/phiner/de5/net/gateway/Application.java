package phiner.de5.net.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "phiner.de5.net.gateway" })
public class Application {

    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Exception e) {
            System.err.println("CRITICAL: Application failed to start. Forcing exit.");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
