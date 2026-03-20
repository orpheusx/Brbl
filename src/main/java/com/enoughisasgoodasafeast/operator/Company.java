package com.enoughisasgoodasafeast.operator;

import java.util.UUID;

//  Table "brbl_users.companies"
//  Column   |           Type           | Collation | Nullable | Default
//  ------------+--------------------------+-----------+----------+---------
//  id         | uuid                     |           | not null |
//  name       | character varying(64)    |           |          |
//  created_at | timestamp with time zone |           | not null | now()
//  updated_at | timestamp with time zone |           | not null | now()
//  Indexes:
//          "companies_t_pkey" PRIMARY KEY, btree (id)
//  Referenced by:
//  TABLE "amalgams" CONSTRAINT "fk_amalgams_claimant_companies" FOREIGN KEY (claimant_id) REFERENCES companies(id)
//  TABLE "customers" CONSTRAINT "fk_customers_t_company_id" FOREIGN KEY (company_id) REFERENCES companies(id)
//  TABLE "push_campaigns" CONSTRAINT "fk_push_campaigns_companies" FOREIGN KEY (company_id) REFERENCES companies(id)
//  TABLE "routes" CONSTRAINT "fk_routes_companies" FOREIGN KEY (company_id) REFERENCES companies(id)
//  TABLE "scripts" CONSTRAINT "fk_scripts_companies" FOREIGN KEY (company_id) REFERENCES companies(id)

public record Company(UUID id, String name, CompanyStatus status) {}
