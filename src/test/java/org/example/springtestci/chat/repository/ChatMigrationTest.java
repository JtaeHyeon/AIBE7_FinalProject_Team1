package org.example.springtestci.chat.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ChatMigrationTest {

  @Test
  void appliesChatMigrationToIsolatedDatabase() throws Exception {
    String url =
        "jdbc:h2:mem:chat_migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "select count(*) from information_schema.tables "
                    + "where table_name in ('chat_rooms', 'chat_messages', 'chat_read_positions')")) {
      result.next();
      assertEquals(3, result.getInt(1));
    }
  }
}
