package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2026102303__add_trade_pending_unique_indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    String database = context.getConnection().getMetaData().getDatabaseProductName();
    if (!"PostgreSQL".equalsIgnoreCase(database)) {
      return;
    }

    try (var statement = context.getConnection().createStatement()) {
      statement.execute(
          "create unique index uq_purchase_requests_pending_room "
              + "on purchase_requests (room_id) where request_status = 'PENDING'");
      statement.execute(
          "create unique index uq_purchase_requests_reserved_listing "
              + "on purchase_requests (listing_id) where transaction_status = 'RESERVED'");
      statement.execute(
          "create unique index uq_ownership_transfers_pending_product "
              + "on ownership_transfers (product_id) where status = 'PENDING'");
    }
  }
}
