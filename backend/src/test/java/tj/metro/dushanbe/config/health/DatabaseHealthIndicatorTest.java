package tj.metro.dushanbe.config.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class DatabaseHealthIndicatorTest {

    @Test
    void healthReturnsUpWhenConnectionWorks() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");

        var indicator = new DatabaseHealthIndicator(dataSource);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("PostgreSQL", health.getDetails().get("database"));
        assertEquals("16.0", health.getDetails().get("version"));
    }

    @Test
    void healthReturnsDownWhenSqlExceptionThrown() throws Exception {
        var dataSource = mock(DataSource.class);

        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("Connection refused"));

        var indicator = new DatabaseHealthIndicator(dataSource);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
    }
}
