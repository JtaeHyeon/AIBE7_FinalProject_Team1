package org.example.springtestci.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/** PostgreSQL 시험의 기동 실패 정리를 기본 H2 검사에서도 검증한다. 실제 거래 저장 검사는 별도 PG CI가 맡는다. */
class InternalTradeOwnershipCleanupTest {
  private final Map<String, Object> previous = new HashMap<>();

  /** 별도 스키마 정리 시험이 통합 시험의 정적 상태를 변경한 채 남지 않도록 보관한다. */
  @BeforeEach
  void saveState() {
    for (String name : new String[] {"schemaSource", "schema", "context"}) {
      previous.put(
          name, ReflectionTestUtils.getField(InternalTradeOwnershipHttpPostgresTest.class, name));
    }
  }

  @AfterEach
  void restoreState() {
    previous.forEach((name, value) -> set(name, value));
  }

  @Test
  void removesSchemaEvenWhenApplicationContextWasNeverCreated() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:cleanup_" + UUID.randomUUID(), "sa", "");
    // 정리 호출의 새 연결에서도 같은 메모리 DB를 사용한다.
    source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE SCHEMA internal_ownership_failed_start");
    set("schemaSource", source);
    set("schema", "internal_ownership_failed_start");
    set("context", null);
    try {
      InternalTradeOwnershipHttpPostgresTest.stop();
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='INTERNAL_OWNERSHIP_FAILED_START'",
              Integer.class));
    } finally {
      jdbc.execute("SHUTDOWN");
    }
  }

  @Test
  void closesContextEvenIfSchemaCleanupFails() throws Exception {
    var source = mock(DriverManagerDataSource.class);
    when(source.getConnection()).thenThrow(new SQLException("test-only cleanup failure"));
    var context = mock(ConfigurableApplicationContext.class);
    set("schemaSource", source);
    set("schema", "internal_ownership_failed_cleanup");
    set("context", context);
    assertThrows(DataAccessException.class, InternalTradeOwnershipHttpPostgresTest::stop);
    verify(context).close();
  }

  private static void set(String field, Object value) {
    ReflectionTestUtils.setField(InternalTradeOwnershipHttpPostgresTest.class, field, value);
  }
}
