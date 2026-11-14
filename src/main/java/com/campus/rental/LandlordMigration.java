package com.campus.rental;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 增量迁移：不认领历史房源、不重置账号、不删除业务记录。 */
@Component
@Order(15)
public class LandlordMigration implements CommandLineRunner {
  private final Store db;

  public LandlordMigration(Store db) {
    this.db = db;
  }

  public void run(String... args) throws Exception {
    column("t_house", "owner_id", "BIGINT REFERENCES t_user(id)");
    column("t_user", "avatar", "VARCHAR(100) NOT NULL DEFAULT ''");
    column("t_contract", "landlord_id", "BIGINT REFERENCES t_user(id)");
    column("t_contract", "tenant_signed", "BOOLEAN NOT NULL DEFAULT FALSE");
    column("t_contract", "landlord_signed", "BOOLEAN NOT NULL DEFAULT FALSE");
    for (String party : new String[]{"landlord", "tenant"}) {
      column("t_contract", party + "_signed_name", "VARCHAR(40)");
      column("t_contract", party + "_signed_time", "TIMESTAMP NULL");
      column("t_contract", party + "_signed_hash", "VARCHAR(64)");
    }
  }

  private void column(String table, String name, String definition) throws Exception {
    try (var c = db.dataSource().getConnection();
        var rs = c.getMetaData().getColumns(c.getCatalog(), null, table, name)) {
      if (!rs.next()) db.update("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
    }
  }
}
