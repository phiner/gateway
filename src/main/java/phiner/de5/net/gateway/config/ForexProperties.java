package phiner.de5.net.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "forex")
public class ForexProperties {
    /**
     * List of instruments to subscribe to.
     */
    private List<String> instruments;

    /**
     * List of bar periods to process.
     */
    private List<String> periods;
}
