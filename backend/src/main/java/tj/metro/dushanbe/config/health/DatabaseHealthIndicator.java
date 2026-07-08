package tj.metro.dushanbe.config.health;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
            DatabaseMetaData metaData = connection.getMetaData();
            return Health.up()
                    .withDetail("database", metaData.getDatabaseProductName())
                    .withDetail("version", metaData.getDatabaseProductVersion())
                    .build();
        } catch (java.sql.SQLException e) {
            return Health.down(e).build();
        }
    }
}
