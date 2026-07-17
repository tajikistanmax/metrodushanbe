package tj.metro.dushanbe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Хеширование паролей операторов консоли. */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt со стоимостью 12: заметно дороже дефолтных 10 при переборе и всё ещё
     * укладывается в десятки миллисекунд на вход. Длина хеша — 60 символов, что
     * соответствует {@code admin_user.password_hash varchar(72)}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
