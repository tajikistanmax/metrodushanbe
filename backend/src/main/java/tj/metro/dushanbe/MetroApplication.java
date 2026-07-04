package tj.metro.dushanbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа backend-приложения «Метро Душанбе» (модульный монолит).
 * Первый срез: модуль network — сетевой каталог (линии, станции, гео-слой).
 */
@SpringBootApplication
public class MetroApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetroApplication.class, args);
    }
}
