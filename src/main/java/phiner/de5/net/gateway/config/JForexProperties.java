package phiner.de5.net.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jforex")
public class JForexProperties {
    /**
     * JNLP URL for JForex platform.
     */
    private String url;

    /**
     * JForex username.
     */
    private String username;

    /**
     * JForex password.
     */
    private String password;
}
