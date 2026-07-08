package tj.metro.dushanbe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (issuerUri == null || issuerUri.isBlank()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            http.csrf(csrf -> csrf.disable());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/v1/admin/**").hasRole("admin")
                    .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .anyRequest().permitAll()
            );
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
