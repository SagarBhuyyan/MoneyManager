package in.bushansirgur.moneymanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "")
public class AppProperties {
    private String jwtSecret;
    private String moneyManagerFrontendUrl;
    private String appActivationUrl;
}
