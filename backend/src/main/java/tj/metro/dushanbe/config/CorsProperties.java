package tj.metro.dushanbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cors")
public class CorsProperties {

    private String allowedOrigins = "http://localhost:3000,http://localhost:3001,http://localhost:3002";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
