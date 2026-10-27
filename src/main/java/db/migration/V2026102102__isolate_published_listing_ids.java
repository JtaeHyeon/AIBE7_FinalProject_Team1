package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 기존 초안 기반 채팅 번호가 새 판매글에 우연히 연결되지 않게 식별자 구간을 분리한다. */
public class V2026102102__isolate_published_listing_ids extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (var statement = context.getConnection().createStatement()) {
      long next;
      try (var rows =
          statement.executeQuery(
              """
          SELECT GREATEST(
            COALESCE((SELECT MAX(id) FROM listings), 0),
            COALESCE((SELECT MAX(id) FROM listing_drafts), 0),
            COALESCE((SELECT MAX(listing_id) FROM chat_rooms), 0))
          """)) {
        rows.next();
        next = Math.addExact(rows.getLong(1), 1);
      }
      // next는 DB에서 읽은 정수만으로 계산한다. H2와 PostgreSQL의 identity 문법을 공유한다.
      statement.execute("ALTER TABLE listings ALTER COLUMN id RESTART WITH " + next);
    }
  }
}
