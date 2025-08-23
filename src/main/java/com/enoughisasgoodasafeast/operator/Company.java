package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

//
//                         Table "brbl_users.companies"
//       Column      |          Type          | Collation | Nullable | Default
//  -----------------+------------------------+-----------+----------+---------
//   id              | uuid                   |           | not null |
//   name            | character varying(64)  |           |          |
//   billing_address | character varying(512) |           |          |
//  Indexes:
//      "companies_pkey" PRIMARY KEY, btree (id)
//  Referenced by:
//      TABLE "customers" CONSTRAINT "fk_customer_company_id" FOREIGN KEY (company_id) REFERENCES companies(id)
//
public record Company(UUID id, String name) {}
