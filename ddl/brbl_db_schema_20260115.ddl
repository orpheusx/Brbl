--
-- PostgreSQL database dump
--

\restrict Jq5Nh4sHf4aVx9ibh3eaFbRUNHQwLGjPDfLeQ3gjiHMNQock5upNaEmajex5X44

-- Dumped from database version 18.1 (Homebrew)
-- Dumped by pg_dump version 18.1 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: brbl_biz; Type: SCHEMA; Schema: -; Owner: brbl_admin
--

CREATE SCHEMA brbl_biz;


ALTER SCHEMA brbl_biz OWNER TO brbl_admin;

--
-- Name: brbl_logic; Type: SCHEMA; Schema: -; Owner: brbl_admin
--

CREATE SCHEMA brbl_logic;


ALTER SCHEMA brbl_logic OWNER TO brbl_admin;

--
-- Name: brbl_logs; Type: SCHEMA; Schema: -; Owner: brbl_admin
--

CREATE SCHEMA brbl_logs;


ALTER SCHEMA brbl_logs OWNER TO brbl_admin;

--
-- Name: brbl_users; Type: SCHEMA; Schema: -; Owner: brbl_admin
--

CREATE SCHEMA brbl_users;


ALTER SCHEMA brbl_users OWNER TO brbl_admin;

--
-- Name: pgroll; Type: SCHEMA; Schema: -; Owner: mark
--

CREATE SCHEMA pgroll;


ALTER SCHEMA pgroll OWNER TO mark;

--
-- Name: customer_status; Type: TYPE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TYPE brbl_logic.customer_status AS ENUM (
    'REQUESTED',
    'ACTIVE',
    'SUSPENDED',
    'LAPSED'
);


ALTER TYPE brbl_logic.customer_status OWNER TO brbl_admin;

--
-- Name: TYPE customer_status; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON TYPE brbl_logic.customer_status IS 'A customer status is REQUESTED until we get email confirmation then ACTIVE until they are SUSPENDED for cause or non-payment indicates they are LAPSED.';


--
-- Name: delivery_status; Type: TYPE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TYPE brbl_logic.delivery_status AS ENUM (
    'PENDING',
    'INFLIGHT',
    'SENT',
    'FAILED'
);


ALTER TYPE brbl_logic.delivery_status OWNER TO brbl_admin;

--
-- Name: route_status; Type: TYPE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TYPE brbl_logic.route_status AS ENUM (
    'REQUESTED',
    'APPROVED',
    'ACTIVE',
    'SUSPENDED',
    'LAPSED'
);


ALTER TYPE brbl_logic.route_status OWNER TO brbl_admin;

--
-- Name: script_status; Type: TYPE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TYPE brbl_logic.script_status AS ENUM (
    'DRAFT',
    'TEST',
    'VALID',
    'STAGE',
    'PROD',
    'INACTIVE'
);


ALTER TYPE brbl_logic.script_status OWNER TO brbl_admin;

--
-- Name: TYPE script_status; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON TYPE brbl_logic.script_status IS 'The supported states for a script. State changes must be made in declared order except for the last which can be transitioned to from any of the preceding states.';


--
-- Name: user_status; Type: TYPE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TYPE brbl_users.user_status AS ENUM (
    'KNOWN',
    'IN',
    'OUT'
);


ALTER TYPE brbl_users.user_status OWNER TO brbl_admin;

--
-- Name: TYPE user_status; Type: COMMENT; Schema: brbl_users; Owner: brbl_admin
--

COMMENT ON TYPE brbl_users.user_status IS 'User is KNOWN until they opt IN. Afterwards, they may opt OUT and thereafter flip between IN and OUT';


--
-- Name: country_code; Type: TYPE; Schema: public; Owner: brbl_admin
--

CREATE TYPE public.country_code AS ENUM (
    'US',
    'CA',
    'MX'
);


ALTER TYPE public.country_code OWNER TO brbl_admin;

--
-- Name: language_code; Type: TYPE; Schema: public; Owner: brbl_admin
--

CREATE TYPE public.language_code AS ENUM (
    'ENG',
    'SPA',
    'FRA',
    'HAT',
    'CMN',
    'YUE',
    'POR',
    'RUS'
);


ALTER TYPE public.language_code OWNER TO brbl_admin;

--
-- Name: TYPE language_code; Type: COMMENT; Schema: public; Owner: brbl_admin
--

COMMENT ON TYPE public.language_code IS 'An enum of the supported ISO-639-3 language codes.';


--
-- Name: platform; Type: TYPE; Schema: public; Owner: brbl_admin
--

CREATE TYPE public.platform AS ENUM (
    'S',
    'B',
    'W',
    'F'
);


ALTER TYPE public.platform OWNER TO brbl_admin;

--
-- Name: find_version_schema(name, integer); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.find_version_schema(p_schema_name name, p_depth integer DEFAULT 0) RETURNS text
    LANGUAGE sql STABLE
    AS $$
    WITH RECURSIVE ancestors AS (
        SELECT
            name,
            COALESCE(migration ->> 'version_schema', name) AS version_schema,
            schema,
            parent,
            0 AS depth
        FROM
            "pgroll".migrations
        WHERE
            name = "pgroll".latest_migration (p_schema_name)
            AND SCHEMA = p_schema_name
        UNION ALL
        SELECT
            m.name,
            COALESCE(m.migration ->> 'version_schema', m.name) AS version_schema,
            m.schema,
            m.parent,
            a.depth + 1
        FROM
            "pgroll".migrations m
            JOIN ancestors a ON m.name = a.parent
                AND m.schema = a.schema
)
        SELECT
            a.version_schema
        FROM
            ancestors a
    WHERE
        EXISTS (
            SELECT
                1
            FROM
                information_schema.schemata s
            WHERE
                s.schema_name = p_schema_name || '_' || a.version_schema)
    ORDER BY
        a.depth ASC OFFSET p_depth
    LIMIT 1;
$$;


ALTER FUNCTION pgroll.find_version_schema(p_schema_name name, p_depth integer) OWNER TO mark;

--
-- Name: is_active_migration_period(name); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.is_active_migration_period(schemaname name) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT
        EXISTS (
            SELECT
                1
            FROM
                "pgroll".migrations
            WHERE
                SCHEMA = schemaname
                AND done = FALSE)
$$;


ALTER FUNCTION pgroll.is_active_migration_period(schemaname name) OWNER TO mark;

--
-- Name: latest_migration(name); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.latest_migration(schemaname name) RETURNS text
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pgroll', 'pg_catalog', 'pg_temp'
    AS $$
    SELECT
        p.name
    FROM
        "pgroll".migrations p
    WHERE
        NOT EXISTS (
            SELECT
                1
            FROM
                "pgroll".migrations c
            WHERE
                SCHEMA = schemaname
                AND c.parent = p.name)
        AND SCHEMA = schemaname
$$;


ALTER FUNCTION pgroll.latest_migration(schemaname name) OWNER TO mark;

--
-- Name: latest_version(name); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.latest_version(schemaname name) RETURNS text
    LANGUAGE sql STABLE
    AS $$
    SELECT
        "pgroll".find_version_schema (schemaname, 0);
$$;


ALTER FUNCTION pgroll.latest_version(schemaname name) OWNER TO mark;

--
-- Name: previous_migration(name); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.previous_migration(schemaname name) RETURNS text
    LANGUAGE sql
    AS $$
    SELECT
        parent
    FROM
        "pgroll".migrations
    WHERE
        SCHEMA = schemaname
        AND name = "pgroll".latest_migration (schemaname);
$$;


ALTER FUNCTION pgroll.previous_migration(schemaname name) OWNER TO mark;

--
-- Name: previous_version(name); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.previous_version(schemaname name) RETURNS text
    LANGUAGE sql STABLE
    AS $$
    SELECT
        "pgroll".find_version_schema (schemaname, 1);
$$;


ALTER FUNCTION pgroll.previous_version(schemaname name) OWNER TO mark;

--
-- Name: raw_migration(); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.raw_migration() RETURNS event_trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pgroll', 'pg_catalog', 'pg_temp'
    AS $$
DECLARE
    schemaname text;
    migration_id text;
BEGIN
    -- Ignore schema changes made by pgroll
    IF (pg_catalog.current_setting('pgroll.no_inferred_migrations', TRUE) = 'TRUE') THEN
        RETURN;
    END IF;
    IF tg_event = 'sql_drop' AND tg_tag = 'DROP SCHEMA' THEN
        -- Take the schema name from the drop schema command
        SELECT
            object_identity INTO schemaname
        FROM
            pg_event_trigger_dropped_objects ();
    ELSIF tg_event = 'sql_drop'
            AND tg_tag != 'ALTER TABLE' THEN
            -- Guess the schema from drop commands
            SELECT
                schema_name INTO schemaname
            FROM
                pg_catalog.pg_event_trigger_dropped_objects ()
            WHERE
                schema_name IS NOT NULL;
    ELSIF tg_event = 'ddl_command_end' THEN
        -- Guess the schema from ddl commands, ignore migrations that touch several schemas
        IF (
            SELECT
                pg_catalog.count(DISTINCT schema_name)
            FROM
                pg_catalog.pg_event_trigger_ddl_commands ()
            WHERE
                schema_name IS NOT NULL) > 1 THEN
            RETURN;
        END IF;
        IF tg_tag = 'CREATE SCHEMA' THEN
            SELECT
                object_identity INTO schemaname
            FROM
                pg_event_trigger_ddl_commands ();
        ELSE
            SELECT
                schema_name INTO schemaname
            FROM
                pg_catalog.pg_event_trigger_ddl_commands ()
            WHERE
                schema_name IS NOT NULL;
        END IF;
    END IF;
    IF schemaname IS NULL THEN
        RETURN;
    END IF;
    -- Ignore migrations done during a migration period
    IF "pgroll".is_active_migration_period (schemaname) THEN
        RETURN;
    END IF;
    -- Remove any duplicate inferred migrations with the same timestamp for this
    -- schema. We assume such migrations are multi-statement batched migrations
    -- and we are only interested in the last one in the batch.
    DELETE FROM "pgroll".migrations
    WHERE SCHEMA = schemaname
        AND created_at = CURRENT_TIMESTAMP
        AND migration_type = 'inferred'
        AND migration -> 'operations' -> 0 -> 'sql' ->> 'up' = current_query();
    -- Someone did a schema change without pgroll, include it in the history
    -- Get the latest non-inferred migration name with microsecond timestamp for ordering
    WITH latest_non_inferred AS (
        SELECT
            name
        FROM
            "pgroll".migrations
        WHERE
            SCHEMA = schemaname
            AND migration_type != 'inferred'
        ORDER BY
            created_at DESC
        LIMIT 1
)
SELECT
    INTO migration_id CASE WHEN EXISTS (
        SELECT
            1
        FROM
            latest_non_inferred) THEN
        pg_catalog.format('%s_%s', (
                SELECT
                    name
                FROM latest_non_inferred), pg_catalog.to_char(pg_catalog.clock_timestamp(), 'YYYYMMDDHH24MISSUS'))
    ELSE
        pg_catalog.format('00000_initial_%s', pg_catalog.to_char(pg_catalog.clock_timestamp(), 'YYYYMMDDHH24MISSUS'))
    END;
    INSERT INTO "pgroll".migrations (schema, name, migration, resulting_schema, done, parent, migration_type, created_at, updated_at)
        VALUES (schemaname, migration_id, pg_catalog.json_build_object('version_schema', 'sql_' || substring(md5(random()::text), 1, 8), 'operations', (
                SELECT
                    pg_catalog.json_agg(pg_catalog.json_build_object('sql', pg_catalog.json_build_object('up', pg_catalog.current_query()))))),
            "pgroll".read_schema (schemaname),
            TRUE,
            "pgroll".latest_migration (schemaname),
            'inferred',
            statement_timestamp(),
            statement_timestamp());
END;
$$;


ALTER FUNCTION pgroll.raw_migration() OWNER TO mark;

--
-- Name: read_schema(text); Type: FUNCTION; Schema: pgroll; Owner: mark
--

CREATE FUNCTION pgroll.read_schema(schemaname text) RETURNS jsonb
    LANGUAGE plpgsql
    AS $$
DECLARE
    tables jsonb;
BEGIN
    SELECT
        json_build_object('name', schemaname, 'tables', (
                SELECT
                    COALESCE(json_object_agg(t.relname, jsonb_strip_nulls (jsonb_build_object('name', t.relname, 'oid', t.oid, 'comment', descr.description, 'columns', (
                                        SELECT
                                            json_object_agg(name, c)
                                    FROM (
                                        SELECT
                                            attr.attname AS name, CASE WHEN attr.attgenerated = '' THEN
                                                pg_get_expr(def.adbin, def.adrelid)
                                            ELSE
                                                NULL
                                            END AS default, NOT (attr.attnotnull
                                                OR tp.typtype = 'd'
                                                AND tp.typnotnull) AS nullable, CASE WHEN 'character varying'::regtype = ANY (ARRAY[attr.atttypid, tp.typelem]) THEN
                                            REPLACE(format_type(attr.atttypid, attr.atttypmod), 'character varying', 'varchar')
                                        WHEN 'timestamp with time zone'::regtype = ANY (ARRAY[attr.atttypid, tp.typelem]) THEN
                                            REPLACE(format_type(attr.atttypid, attr.atttypmod), 'timestamp with time zone', 'timestamptz')
                                        ELSE
                                            format_type(attr.atttypid, attr.atttypmod)
                                        END AS type, descr.description AS comment, (EXISTS (
                                                SELECT
                                                    1
                                                FROM pg_constraint
                                            WHERE
                                                conrelid = attr.attrelid
                                                AND ARRAY[attr.attnum::int] @> conkey::int[]
                                                AND contype = 'u')
                                        OR EXISTS (
                                            SELECT
                                                1
                                            FROM pg_index
                                            JOIN pg_class ON pg_class.oid = pg_index.indexrelid
                                        WHERE
                                            indrelid = attr.attrelid
                                            AND indisunique
                                            AND ARRAY[attr.attnum::int] @> pg_index.indkey::int[])) AS unique, (
                                    SELECT
                                        array_agg(e.enumlabel ORDER BY e.enumsortorder)
                                    FROM pg_enum AS e
                                WHERE
                                    e.enumtypid = tp.oid) AS enumValues, CASE WHEN tp.typtype = 'b' THEN
                                    'base'
                                WHEN tp.typtype = 'c' THEN
                                    'composite'
                                WHEN tp.typtype = 'd' THEN
                                    'domain'
                                WHEN tp.typtype = 'e' THEN
                                    'enum'
                                WHEN tp.typtype = 'p' THEN
                                    'pseudo'
                                WHEN tp.typtype = 'r' THEN
                                    'range'
                                WHEN tp.typtype = 'm' THEN
                                    'multirange'
                                END AS postgresType FROM pg_attribute AS attr
                                INNER JOIN pg_type AS tp ON attr.atttypid = tp.oid
                                LEFT JOIN pg_attrdef AS def ON attr.attrelid = def.adrelid
                                    AND attr.attnum = def.adnum
                            LEFT JOIN pg_description AS descr ON attr.attrelid = descr.objoid
                                AND attr.attnum = descr.objsubid
                        WHERE
                            attr.attnum > 0
                            AND NOT attr.attisdropped
                            AND attr.attrelid = t.oid ORDER BY attr.attnum) c), 'primaryKey', (
                        SELECT
                            json_agg(pg_attribute.attname) AS primary_key_columns
                        FROM pg_index, pg_attribute
                    WHERE
                        indrelid = t.oid
                        AND nspname = schemaname
                        AND pg_attribute.attrelid = t.oid
                        AND pg_attribute.attnum = ANY (pg_index.indkey)
                        AND indisprimary), 'indexes', (
                        SELECT
                            json_object_agg(ix_details.name, json_build_object('name', ix_details.name, 'unique', ix_details.indisunique, 'exclusion', ix_details.indisexclusion, 'columns', ix_details.columns, 'predicate', ix_details.predicate, 'method', ix_details.method, 'definition', ix_details.definition))
                    FROM (
                        SELECT
                            replace(reverse(split_part(reverse(pi.indexrelid::regclass::text), '.', 1)), '"', '') AS name, pi.indisunique, pi.indisexclusion, array_agg(a.attname) AS columns, pg_get_expr(pi.indpred, t.oid) AS predicate, am.amname AS method, pg_get_indexdef(pi.indexrelid) AS definition
                        FROM pg_index pi
                        JOIN pg_attribute a ON a.attrelid = pi.indrelid
                            AND a.attnum = ANY (pi.indkey)
                        JOIN pg_class cls ON cls.oid = pi.indexrelid
                        JOIN pg_am am ON am.oid = cls.relam
                        WHERE
                            indrelid = t.oid::regclass GROUP BY pi.indexrelid, pi.indisunique, pi.indpred, am.amname) AS ix_details), 'checkConstraints', (
                SELECT
                    json_object_agg(cc_details.conname, json_build_object('name', cc_details.conname, 'columns', cc_details.columns, 'definition', cc_details.definition, 'noInherit', cc_details.connoinherit))
                FROM (
                    SELECT
                        cc_constraint.conname, array_agg(cc_attr.attname ORDER BY cc_constraint.conkey::int[]) AS columns, pg_get_constraintdef(cc_constraint.oid) AS definition, cc_constraint.connoinherit FROM pg_constraint AS cc_constraint
                    INNER JOIN pg_attribute cc_attr ON cc_attr.attrelid = cc_constraint.conrelid
                        AND cc_attr.attnum = ANY (cc_constraint.conkey)
                    WHERE
                        cc_constraint.conrelid = t.oid
                        AND cc_constraint.contype = 'c' GROUP BY cc_constraint.oid, cc_constraint.conname) AS cc_details), 'uniqueConstraints', (
                    SELECT
                        json_object_agg(uc_details.conname, json_build_object('name', uc_details.conname, 'columns', uc_details.columns))
                    FROM (
                        SELECT
                            uc_constraint.conname, array_agg(uc_attr.attname ORDER BY uc_constraint.conkey::int[]) AS columns, pg_get_constraintdef(uc_constraint.oid) AS definition FROM pg_constraint AS uc_constraint
                        INNER JOIN pg_attribute uc_attr ON uc_attr.attrelid = uc_constraint.conrelid
                            AND uc_attr.attnum = ANY (uc_constraint.conkey)
                        WHERE
                            uc_constraint.conrelid = t.oid
                            AND uc_constraint.contype = 'u' GROUP BY uc_constraint.oid, uc_constraint.conname) AS uc_details), 'excludeConstraints', (
                        SELECT
                            json_object_agg(xc_details.conname, json_build_object('name', xc_details.conname, 'columns', xc_details.columns, 'definition', xc_details.definition, 'predicate', xc_details.predicate, 'method', xc_details.method))
                        FROM (
                            SELECT
                                xc_constraint.conname, array_agg(xc_attr.attname ORDER BY xc_constraint.conkey::int[]) AS columns, pg_get_expr(pi.indpred, t.oid) AS predicate, am.amname AS method, pg_get_constraintdef(xc_constraint.oid) AS definition FROM pg_constraint AS xc_constraint
                            INNER JOIN pg_attribute xc_attr ON xc_attr.attrelid = xc_constraint.conrelid
                                AND xc_attr.attnum = ANY (xc_constraint.conkey)
                            JOIN pg_index pi ON pi.indexrelid = xc_constraint.conindid
                            JOIN pg_class cls ON cls.oid = pi.indexrelid
                            JOIN pg_am am ON am.oid = cls.relam
                            WHERE
                                xc_constraint.conrelid = t.oid
                                AND xc_constraint.contype = 'x' GROUP BY xc_constraint.oid, xc_constraint.conname, pi.indpred, pi.indexrelid, am.amname) AS xc_details), 'foreignKeys', (
                            SELECT
                                json_object_agg(fk_details.conname, json_build_object('name', fk_details.conname, 'columns', fk_details.columns, 'referencedTable', fk_details.referencedTable, 'referencedColumns', fk_details.referencedColumns, 'matchType', fk_details.matchType, 'onDelete', fk_details.onDelete, 'onUpdate', fk_details.onUpdate))
                            FROM (
                                SELECT
                                    fk_info.conname AS conname, fk_info.columns AS columns, fk_info.relname AS referencedTable, array_agg(ref_attr.attname ORDER BY ref_attr.attname) AS referencedColumns, CASE WHEN fk_info.confmatchtype = 'f' THEN
                                    'FULL'
                                WHEN fk_info.confmatchtype = 'p' THEN
                                    'PARTIAL'
                                WHEN fk_info.confmatchtype = 's' THEN
                                    'SIMPLE'
                                END AS matchType, CASE WHEN fk_info.confdeltype = 'a' THEN
                                    'NO ACTION'
                                WHEN fk_info.confdeltype = 'r' THEN
                                    'RESTRICT'
                                WHEN fk_info.confdeltype = 'c' THEN
                                    'CASCADE'
                                WHEN fk_info.confdeltype = 'd' THEN
                                    'SET DEFAULT'
                                WHEN fk_info.confdeltype = 'n' THEN
                                    'SET NULL'
                                END AS onDelete, CASE WHEN fk_info.confupdtype = 'a' THEN
                                    'NO ACTION'
                                WHEN fk_info.confupdtype = 'r' THEN
                                    'RESTRICT'
                                WHEN fk_info.confupdtype = 'c' THEN
                                    'CASCADE'
                                WHEN fk_info.confupdtype = 'd' THEN
                                    'SET DEFAULT'
                                WHEN fk_info.confupdtype = 'n' THEN
                                    'SET NULL'
                                END AS onUpdate FROM (
                                    SELECT
                                        fk_constraint.conname, fk_constraint.conrelid, fk_constraint.confrelid, fk_constraint.confkey, fk_cl.relname, fk_constraint.confmatchtype, fk_constraint.confdeltype, fk_constraint.confupdtype, array_agg(fk_attr.attname ORDER BY fk_attr.attname) AS columns FROM pg_constraint AS fk_constraint
                                    INNER JOIN pg_class fk_cl ON fk_constraint.confrelid = fk_cl.oid -- join the referenced table
                                    INNER JOIN pg_attribute fk_attr ON fk_attr.attrelid = fk_constraint.conrelid
                                        AND fk_attr.attnum = ANY (fk_constraint.conkey) -- join the columns of the referencing table
                                    WHERE
                                        fk_constraint.conrelid = t.oid
                                        AND fk_constraint.contype = 'f' GROUP BY fk_constraint.conrelid, fk_constraint.conname, fk_constraint.confrelid, fk_cl.relname, fk_constraint.confkey, fk_constraint.confmatchtype, fk_constraint.confdeltype, fk_constraint.confupdtype) AS fk_info
                                    INNER JOIN pg_attribute ref_attr ON ref_attr.attrelid = fk_info.confrelid
                                        AND ref_attr.attnum = ANY (fk_info.confkey) -- join the columns of the referenced table
                                GROUP BY fk_info.conname, fk_info.conrelid, fk_info.columns, fk_info.confrelid, fk_info.confmatchtype, fk_info.confdeltype, fk_info.confupdtype, fk_info.relname) AS fk_details)))), '{}'::json)
            FROM pg_class AS t
            INNER JOIN pg_namespace AS ns ON t.relnamespace = ns.oid
            LEFT JOIN pg_description AS descr ON t.oid = descr.objoid
                AND descr.objsubid = 0
            WHERE
                ns.nspname = schemaname
                AND t.relkind IN ('r', 'p') -- tables only (ignores views, materialized views & foreign tables)
)) INTO tables;
    RETURN tables;
END;
$$;


ALTER FUNCTION pgroll.read_schema(schemaname text) OWNER TO mark;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: addresses; Type: TABLE; Schema: brbl_biz; Owner: brbl_admin
--

CREATE TABLE brbl_biz.addresses (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    street_1 character varying(48) NOT NULL,
    street_2 character varying(48),
    city character varying(48) NOT NULL,
    state_province character varying(48) NOT NULL,
    postal_code character varying(24) NOT NULL,
    country public.country_code NOT NULL
);


ALTER TABLE brbl_biz.addresses OWNER TO brbl_admin;

--
-- Name: customer_addresses; Type: TABLE; Schema: brbl_biz; Owner: brbl_admin
--

CREATE TABLE brbl_biz.customer_addresses (
    customer_id uuid NOT NULL,
    address_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    active boolean DEFAULT true NOT NULL
);


ALTER TABLE brbl_biz.customer_addresses OWNER TO brbl_admin;

--
-- Name: payment_cc; Type: TABLE; Schema: brbl_biz; Owner: brbl_admin
--

CREATE TABLE brbl_biz.payment_cc (
    id uuid NOT NULL,
    card_number character varying(20) NOT NULL,
    name character varying(73) NOT NULL,
    expiry date NOT NULL,
    code character varying(4)
);


ALTER TABLE brbl_biz.payment_cc OWNER TO brbl_admin;

--
-- Name: campaign_users; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.campaign_users (
    campaign_id uuid,
    user_id uuid,
    delivered brbl_logic.delivery_status
);


ALTER TABLE brbl_logic.campaign_users OWNER TO brbl_admin;

--
-- Name: default_scripts; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.default_scripts (
    id uuid NOT NULL,
    platform public.platform NOT NULL,
    channel character varying(15) NOT NULL,
    customer_id uuid NOT NULL,
    node_id uuid NOT NULL
);


ALTER TABLE brbl_logic.default_scripts OWNER TO brbl_admin;

--
-- Name: edges; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.edges (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    match_text character varying(128),
    response_text character varying(255),
    src uuid,
    dst uuid,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.edges OWNER TO brbl_admin;

--
-- Name: keywords; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.keywords (
    id uuid NOT NULL,
    pattern character varying(128),
    platform public.platform NOT NULL,
    script_id uuid NOT NULL,
    channel character varying(15) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE brbl_logic.keywords OWNER TO brbl_admin;

--
-- Name: COLUMN keywords.channel; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.keywords.channel IS 'The short (5-6 digits) or long (10 digit) code for which the keyword is scoped. Global if none is specified.';


--
-- Name: nodes; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.nodes (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    text character varying(255),
    type smallint DEFAULT 1 NOT NULL,
    label character varying(32),
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.nodes OWNER TO brbl_admin;

--
-- Name: old_routes; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.old_routes (
    id uuid NOT NULL,
    platform public.platform NOT NULL,
    channel character varying(15) NOT NULL,
    default_node_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    status brbl_logic.route_status DEFAULT 'REQUESTED'::brbl_logic.route_status NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.old_routes OWNER TO brbl_admin;

--
-- Name: old_scripts; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.old_scripts (
    id uuid NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(128),
    customer_id uuid,
    node_id uuid,
    status brbl_logic.script_status,
    language public.language_code DEFAULT 'ENG'::public.language_code,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.old_scripts OWNER TO brbl_admin;

--
-- Name: COLUMN old_scripts.name; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.name IS 'The title of the script. Ideally unique by customer.';


--
-- Name: COLUMN old_scripts.description; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.description IS 'An optional description of what the script does.';


--
-- Name: COLUMN old_scripts.customer_id; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.customer_id IS 'The creator/owner of the script.';


--
-- Name: COLUMN old_scripts.node_id; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.node_id IS 'The initial node in the script graph.';


--
-- Name: COLUMN old_scripts.status; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.status IS 'Describes the stage of readiness of the script. See script_status type';


--
-- Name: COLUMN old_scripts.language; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.old_scripts.language IS 'The language in which the script is written. See language_code type.';


--
-- Name: push_campaigns; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.push_campaigns (
    id uuid NOT NULL,
    customer_id uuid,
    description character varying(64),
    script_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone
);


ALTER TABLE brbl_logic.push_campaigns OWNER TO brbl_admin;

--
-- Name: routes; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.routes (
    id uuid NOT NULL,
    platform public.platform NOT NULL,
    channel character varying(15) NOT NULL,
    default_node_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    status brbl_logic.route_status DEFAULT 'REQUESTED'::brbl_logic.route_status NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.routes OWNER TO brbl_admin;

--
-- Name: schedules; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.schedules (
    id uuid NOT NULL,
    expression character varying(24) NOT NULL,
    is_recurring boolean DEFAULT false NOT NULL,
    script_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.schedules OWNER TO brbl_admin;

--
-- Name: COLUMN schedules.expression; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.schedules.expression IS 'The cron string describing the schedule.';


--
-- Name: COLUMN schedules.is_recurring; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.schedules.is_recurring IS 'TRUE is recurring, FALSE is one-time execution';


--
-- Name: COLUMN schedules.script_id; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.schedules.script_id IS 'The script to be processed.';


--
-- Name: scripts; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.scripts (
    id uuid NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(128),
    customer_id uuid,
    node_id uuid,
    status brbl_logic.script_status,
    language public.language_code,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.scripts OWNER TO brbl_admin;

--
-- Name: sessions; Type: TABLE; Schema: brbl_logic; Owner: brbl_admin
--

CREATE TABLE brbl_logic.sessions (
    group_id uuid NOT NULL,
    data bytea NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logic.sessions OWNER TO brbl_admin;

--
-- Name: COLUMN sessions.group_id; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.sessions.group_id IS 'Effectively (but not actually) the foreign key to brbl_logic.users.';


--
-- Name: COLUMN sessions.data; Type: COMMENT; Schema: brbl_logic; Owner: brbl_admin
--

COMMENT ON COLUMN brbl_logic.sessions.data IS 'The latest serialized session data for the referenced user.';


--
-- Name: messages_mo; Type: TABLE; Schema: brbl_logs; Owner: brbl_admin
--

CREATE TABLE brbl_logs.messages_mo (
    id uuid NOT NULL,
    rcvd_at timestamp with time zone NOT NULL,
    _from character varying(15) NOT NULL,
    _to character varying(15) NOT NULL,
    _text character varying(2000) NOT NULL
);


ALTER TABLE brbl_logs.messages_mo OWNER TO brbl_admin;

--
-- Name: messages_mo_prcd; Type: TABLE; Schema: brbl_logs; Owner: brbl_admin
--

CREATE TABLE brbl_logs.messages_mo_prcd (
    id uuid NOT NULL,
    prcd_at timestamp with time zone NOT NULL,
    session_id uuid NOT NULL,
    script_id uuid NOT NULL
);


ALTER TABLE brbl_logs.messages_mo_prcd OWNER TO brbl_admin;

--
-- Name: messages_mt; Type: TABLE; Schema: brbl_logs; Owner: brbl_admin
--

CREATE TABLE brbl_logs.messages_mt (
    id uuid NOT NULL,
    sent_at timestamp with time zone NOT NULL,
    _from character varying(15) NOT NULL,
    _to character varying(15) NOT NULL,
    _text character varying(2000) NOT NULL,
    session_id uuid NOT NULL,
    script_id uuid NOT NULL
);


ALTER TABLE brbl_logs.messages_mt OWNER TO brbl_admin;

--
-- Name: messages_mt_dlvr; Type: TABLE; Schema: brbl_logs; Owner: brbl_admin
--

CREATE TABLE brbl_logs.messages_mt_dlvr (
    id uuid NOT NULL,
    dlvr_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_logs.messages_mt_dlvr OWNER TO brbl_admin;

--
-- Name: amalgams; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.amalgams (
    group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    profile_id uuid,
    customer_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_users.amalgams OWNER TO brbl_admin;

--
-- Name: companies; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.companies (
    id uuid NOT NULL,
    name character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE brbl_users.companies OWNER TO brbl_admin;

--
-- Name: customers; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.customers (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    email character varying(64) NOT NULL,
    company_id uuid,
    status brbl_logic.customer_status DEFAULT 'REQUESTED'::brbl_logic.customer_status NOT NULL,
    confirmation_code character varying(12) NOT NULL,
    password character varying(72)
);


ALTER TABLE brbl_users.customers OWNER TO brbl_admin;

--
-- Name: old_companies; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.old_companies (
    id uuid NOT NULL,
    name character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE brbl_users.old_companies OWNER TO brbl_admin;

--
-- Name: old_customers; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.old_customers (
    id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    profile_id uuid,
    email character varying(64) NOT NULL,
    company_id uuid,
    status brbl_logic.customer_status DEFAULT 'REQUESTED'::brbl_logic.customer_status NOT NULL,
    confirmation_code character varying(12) NOT NULL,
    password character varying(72)
);


ALTER TABLE brbl_users.old_customers OWNER TO brbl_admin;

--
-- Name: old_profiles; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.old_profiles (
    id uuid NOT NULL,
    surname character varying(36),
    given_name character varying(36),
    other_languages character varying(23),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    roles character varying(64)
);


ALTER TABLE brbl_users.old_profiles OWNER TO brbl_admin;

--
-- Name: old_users; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.old_users (
    group_id uuid NOT NULL,
    platform_id character varying(36) NOT NULL,
    platform_code public.platform NOT NULL,
    country public.country_code NOT NULL,
    language public.language_code NOT NULL,
    nickname character varying(36),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    status brbl_users.user_status DEFAULT 'KNOWN'::brbl_users.user_status NOT NULL,
    customer_id uuid NOT NULL,
    id uuid NOT NULL
);


ALTER TABLE brbl_users.old_users OWNER TO brbl_admin;

--
-- Name: profiles; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.profiles (
    id uuid NOT NULL,
    surname character varying(36),
    given_name character varying(36),
    other_languages character varying(23),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    roles character varying(64)
);


ALTER TABLE brbl_users.profiles OWNER TO brbl_admin;

--
-- Name: users; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.users (
    id uuid NOT NULL,
    platform_code public.platform NOT NULL,
    platform_id character varying(36) NOT NULL,
    country public.country_code NOT NULL,
    language public.language_code NOT NULL,
    nickname character varying(36),
    status brbl_users.user_status DEFAULT 'KNOWN'::brbl_users.user_status NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE brbl_users.users OWNER TO brbl_admin;

--
-- Name: migrations; Type: TABLE; Schema: pgroll; Owner: mark
--

CREATE TABLE pgroll.migrations (
    schema name NOT NULL,
    name text NOT NULL,
    migration jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    parent text,
    done boolean DEFAULT false NOT NULL,
    resulting_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    migration_type character varying(32) DEFAULT 'pgroll'::character varying,
    CONSTRAINT migration_type_check CHECK (((migration_type)::text = ANY ((ARRAY['pgroll'::character varying, 'inferred'::character varying, 'baseline'::character varying])::text[])))
);


ALTER TABLE pgroll.migrations OWNER TO mark;

--
-- Name: pgroll_version; Type: TABLE; Schema: pgroll; Owner: mark
--

CREATE TABLE pgroll.pgroll_version (
    version text NOT NULL,
    initialized_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE pgroll.pgroll_version OWNER TO mark;

--
-- Name: addresses addresses_pkey; Type: CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);


--
-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (customer_id, address_id);


--
-- Name: payment_cc payment_cc_pkey; Type: CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.payment_cc
    ADD CONSTRAINT payment_cc_pkey PRIMARY KEY (id);


--
-- Name: payment_cc unique_card_number_expiry; Type: CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.payment_cc
    ADD CONSTRAINT unique_card_number_expiry UNIQUE (card_number, expiry);


--
-- Name: default_scripts default_scripts_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.default_scripts
    ADD CONSTRAINT default_scripts_pkey PRIMARY KEY (id);


--
-- Name: edges edges_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.edges
    ADD CONSTRAINT edges_pkey PRIMARY KEY (id);


--
-- Name: keywords keywords_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.keywords
    ADD CONSTRAINT keywords_pkey PRIMARY KEY (id);


--
-- Name: push_campaigns push_campaigns_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.push_campaigns
    ADD CONSTRAINT push_campaigns_pkey PRIMARY KEY (id);


--
-- Name: old_routes routes_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_routes
    ADD CONSTRAINT routes_pkey PRIMARY KEY (id);


--
-- Name: routes routes_t_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.routes
    ADD CONSTRAINT routes_t_pkey PRIMARY KEY (id);


--
-- Name: schedules schedules_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.schedules
    ADD CONSTRAINT schedules_pkey PRIMARY KEY (id);


--
-- Name: nodes scripts_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.nodes
    ADD CONSTRAINT scripts_pkey PRIMARY KEY (id);


--
-- Name: old_scripts scripts_pkey1; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_scripts
    ADD CONSTRAINT scripts_pkey1 PRIMARY KEY (id);


--
-- Name: scripts scripts_t_pkey1; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.scripts
    ADD CONSTRAINT scripts_t_pkey1 PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (group_id);


--
-- Name: default_scripts unique_platform_channel; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.default_scripts
    ADD CONSTRAINT unique_platform_channel UNIQUE (platform, channel);


--
-- Name: keywords unique_platform_shortcode_pattern; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.keywords
    ADD CONSTRAINT unique_platform_shortcode_pattern UNIQUE (platform, channel, pattern);


--
-- Name: old_routes unique_routes_platform_channel; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_routes
    ADD CONSTRAINT unique_routes_platform_channel UNIQUE (platform, channel);


--
-- Name: routes unique_routes_t_platform_channel; Type: CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.routes
    ADD CONSTRAINT unique_routes_t_platform_channel UNIQUE (platform, channel);


--
-- Name: old_companies companies_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_companies
    ADD CONSTRAINT companies_pkey PRIMARY KEY (id);


--
-- Name: companies companies_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.companies
    ADD CONSTRAINT companies_t_pkey PRIMARY KEY (id);


--
-- Name: old_customers customers_confirmation_code_key; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_customers
    ADD CONSTRAINT customers_confirmation_code_key UNIQUE (confirmation_code);


--
-- Name: old_customers customers_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: customers customers_t_confirmation_code_key; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.customers
    ADD CONSTRAINT customers_t_confirmation_code_key UNIQUE (confirmation_code);


--
-- Name: customers customers_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.customers
    ADD CONSTRAINT customers_t_pkey PRIMARY KEY (id);


--
-- Name: old_profiles profiles_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);


--
-- Name: profiles profiles_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.profiles
    ADD CONSTRAINT profiles_t_pkey PRIMARY KEY (id);


--
-- Name: customers unique_customers_t_email; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.customers
    ADD CONSTRAINT unique_customers_t_email UNIQUE (email);


--
-- Name: old_customers unique_email; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_customers
    ADD CONSTRAINT unique_email UNIQUE (email);


--
-- Name: amalgams unique_group_user_profile_customer; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.amalgams
    ADD CONSTRAINT unique_group_user_profile_customer UNIQUE (group_id, user_id, profile_id, customer_id);


--
-- Name: old_users users_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: old_users users_platform_customer_id_unique; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_users
    ADD CONSTRAINT users_platform_customer_id_unique UNIQUE (platform_code, customer_id, platform_id);


--
-- Name: users users_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.users
    ADD CONSTRAINT users_t_pkey PRIMARY KEY (id);


--
-- Name: migrations migrations_pkey; Type: CONSTRAINT; Schema: pgroll; Owner: mark
--

ALTER TABLE ONLY pgroll.migrations
    ADD CONSTRAINT migrations_pkey PRIMARY KEY (schema, name);


--
-- Name: pgroll_version pgroll_version_pkey; Type: CONSTRAINT; Schema: pgroll; Owner: mark
--

ALTER TABLE ONLY pgroll.pgroll_version
    ADD CONSTRAINT pgroll_version_pkey PRIMARY KEY (version);


--
-- Name: history_is_linear; Type: INDEX; Schema: pgroll; Owner: mark
--

CREATE UNIQUE INDEX history_is_linear ON pgroll.migrations USING btree (schema, parent);


--
-- Name: only_first_migration_without_parent; Type: INDEX; Schema: pgroll; Owner: mark
--

CREATE UNIQUE INDEX only_first_migration_without_parent ON pgroll.migrations USING btree (schema) WHERE (parent IS NULL);


--
-- Name: only_one_active; Type: INDEX; Schema: pgroll; Owner: mark
--

CREATE UNIQUE INDEX only_one_active ON pgroll.migrations USING btree (schema, name, done) WHERE (done = false);


--
-- Name: customer_addresses customer_addresses_address_id_fkey; Type: FK CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.customer_addresses
    ADD CONSTRAINT customer_addresses_address_id_fkey FOREIGN KEY (address_id) REFERENCES brbl_biz.addresses(id);


--
-- Name: customer_addresses customer_addresses_customer_id_fkey; Type: FK CONSTRAINT; Schema: brbl_biz; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_biz.customer_addresses
    ADD CONSTRAINT customer_addresses_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES brbl_users.customers(id);


--
-- Name: push_campaigns fk_campaign_customers_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.push_campaigns
    ADD CONSTRAINT fk_campaign_customers_id FOREIGN KEY (customer_id) REFERENCES brbl_users.customers(id);


--
-- Name: push_campaigns fk_campaign_scripts_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.push_campaigns
    ADD CONSTRAINT fk_campaign_scripts_id FOREIGN KEY (script_id) REFERENCES brbl_logic.scripts(id);


--
-- Name: campaign_users fk_campaign_users_campaign_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.campaign_users
    ADD CONSTRAINT fk_campaign_users_campaign_id FOREIGN KEY (campaign_id) REFERENCES brbl_logic.push_campaigns(id);


--
-- Name: campaign_users fk_campaign_users_user_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.campaign_users
    ADD CONSTRAINT fk_campaign_users_user_id FOREIGN KEY (user_id) REFERENCES brbl_users.users(id);


--
-- Name: old_scripts fk_customers_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_scripts
    ADD CONSTRAINT fk_customers_id FOREIGN KEY (customer_id) REFERENCES brbl_users.old_customers(id);


--
-- Name: scripts fk_customers_t_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.scripts
    ADD CONSTRAINT fk_customers_t_id FOREIGN KEY (customer_id) REFERENCES brbl_users.customers(id);


--
-- Name: default_scripts fk_default_scripts_customer_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.default_scripts
    ADD CONSTRAINT fk_default_scripts_customer_id FOREIGN KEY (customer_id) REFERENCES brbl_users.old_customers(id);


--
-- Name: default_scripts fk_default_scripts_nodes_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.default_scripts
    ADD CONSTRAINT fk_default_scripts_nodes_id FOREIGN KEY (node_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: edges fk_node_dst; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.edges
    ADD CONSTRAINT fk_node_dst FOREIGN KEY (dst) REFERENCES brbl_logic.nodes(id);


--
-- Name: edges fk_node_src; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.edges
    ADD CONSTRAINT fk_node_src FOREIGN KEY (src) REFERENCES brbl_logic.nodes(id);


--
-- Name: old_scripts fk_nodes_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_scripts
    ADD CONSTRAINT fk_nodes_id FOREIGN KEY (node_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: scripts fk_nodes_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.scripts
    ADD CONSTRAINT fk_nodes_id FOREIGN KEY (node_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: old_routes fk_routes_customers_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_routes
    ADD CONSTRAINT fk_routes_customers_id FOREIGN KEY (customer_id) REFERENCES brbl_users.old_customers(id);


--
-- Name: old_routes fk_routes_nodes_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.old_routes
    ADD CONSTRAINT fk_routes_nodes_id FOREIGN KEY (default_node_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: routes fk_routes_nodes_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.routes
    ADD CONSTRAINT fk_routes_nodes_id FOREIGN KEY (default_node_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: routes fk_routes_t_customers_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.routes
    ADD CONSTRAINT fk_routes_t_customers_id FOREIGN KEY (customer_id) REFERENCES brbl_users.customers(id);


--
-- Name: keywords fk_script_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.keywords
    ADD CONSTRAINT fk_script_id FOREIGN KEY (script_id) REFERENCES brbl_logic.nodes(id);


--
-- Name: schedules fk_scripts_id; Type: FK CONSTRAINT; Schema: brbl_logic; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_logic.schedules
    ADD CONSTRAINT fk_scripts_id FOREIGN KEY (script_id) REFERENCES brbl_logic.scripts(id);


--
-- Name: old_customers fk_customer_company_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_customers
    ADD CONSTRAINT fk_customer_company_id FOREIGN KEY (company_id) REFERENCES brbl_users.old_companies(id);


--
-- Name: old_users fk_customer_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_users
    ADD CONSTRAINT fk_customer_id FOREIGN KEY (customer_id) REFERENCES brbl_users.old_customers(id);


--
-- Name: old_customers fk_customer_profile_group_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.old_customers
    ADD CONSTRAINT fk_customer_profile_group_id FOREIGN KEY (profile_id) REFERENCES brbl_users.old_profiles(id);


--
-- Name: customers fk_customers_t_company_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.customers
    ADD CONSTRAINT fk_customers_t_company_id FOREIGN KEY (company_id) REFERENCES brbl_users.companies(id);


--
-- Name: amalgams fk_users_t_customer_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.amalgams
    ADD CONSTRAINT fk_users_t_customer_id FOREIGN KEY (customer_id) REFERENCES brbl_users.customers(id);


--
-- Name: amalgams fk_users_t_profile_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.amalgams
    ADD CONSTRAINT fk_users_t_profile_id FOREIGN KEY (profile_id) REFERENCES brbl_users.profiles(id);


--
-- Name: amalgams fk_users_t_user_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--

ALTER TABLE ONLY brbl_users.amalgams
    ADD CONSTRAINT fk_users_t_user_id FOREIGN KEY (user_id) REFERENCES brbl_users.users(id);


--
-- Name: migrations migrations_schema_parent_fkey; Type: FK CONSTRAINT; Schema: pgroll; Owner: mark
--

ALTER TABLE ONLY pgroll.migrations
    ADD CONSTRAINT migrations_schema_parent_fkey FOREIGN KEY (schema, parent) REFERENCES pgroll.migrations(schema, name);


--
-- Name: SCHEMA brbl_biz; Type: ACL; Schema: -; Owner: brbl_admin
--

GRANT USAGE ON SCHEMA brbl_biz TO brbl_biz_read_role;
GRANT USAGE ON SCHEMA brbl_biz TO brbl_biz_write_role;


--
-- Name: SCHEMA brbl_logic; Type: ACL; Schema: -; Owner: brbl_admin
--

GRANT USAGE ON SCHEMA brbl_logic TO brbl_logic_read_role;


--
-- Name: SCHEMA brbl_logs; Type: ACL; Schema: -; Owner: brbl_admin
--

GRANT USAGE ON SCHEMA brbl_logs TO brbl_logs_write_role;


--
-- Name: SCHEMA brbl_users; Type: ACL; Schema: -; Owner: brbl_admin
--

GRANT USAGE ON SCHEMA brbl_users TO brbl_user_rw_role;


--
-- Name: TABLE addresses; Type: ACL; Schema: brbl_biz; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_biz.addresses TO brbl_biz_read_role;
GRANT INSERT,UPDATE ON TABLE brbl_biz.addresses TO brbl_biz_write_role;


--
-- Name: TABLE customer_addresses; Type: ACL; Schema: brbl_biz; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_biz.customer_addresses TO brbl_biz_read_role;
GRANT INSERT,UPDATE ON TABLE brbl_biz.customer_addresses TO brbl_biz_write_role;


--
-- Name: TABLE payment_cc; Type: ACL; Schema: brbl_biz; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_biz.payment_cc TO brbl_biz_read_role;
GRANT INSERT,UPDATE ON TABLE brbl_biz.payment_cc TO brbl_biz_write_role;


--
-- Name: TABLE edges; Type: ACL; Schema: brbl_logic; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_logic.edges TO brbl_logic_read_role;


--
-- Name: TABLE keywords; Type: ACL; Schema: brbl_logic; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_logic.keywords TO brbl_logic_read_role;


--
-- Name: TABLE nodes; Type: ACL; Schema: brbl_logic; Owner: brbl_admin
--

GRANT SELECT ON TABLE brbl_logic.nodes TO brbl_logic_read_role;


--
-- Name: TABLE messages_mo; Type: ACL; Schema: brbl_logs; Owner: brbl_admin
--

GRANT INSERT ON TABLE brbl_logs.messages_mo TO brbl_logs_write_role;


--
-- Name: TABLE messages_mo_prcd; Type: ACL; Schema: brbl_logs; Owner: brbl_admin
--

GRANT INSERT ON TABLE brbl_logs.messages_mo_prcd TO brbl_logs_write_role;


--
-- Name: TABLE messages_mt; Type: ACL; Schema: brbl_logs; Owner: brbl_admin
--

GRANT INSERT ON TABLE brbl_logs.messages_mt TO brbl_logs_write_role;


--
-- Name: TABLE messages_mt_dlvr; Type: ACL; Schema: brbl_logs; Owner: brbl_admin
--

GRANT INSERT ON TABLE brbl_logs.messages_mt_dlvr TO brbl_logs_write_role;


--
-- Name: TABLE old_profiles; Type: ACL; Schema: brbl_users; Owner: brbl_admin
--

GRANT SELECT,INSERT ON TABLE brbl_users.old_profiles TO brbl_user_rw_role;


--
-- Name: TABLE old_users; Type: ACL; Schema: brbl_users; Owner: brbl_admin
--

GRANT SELECT,INSERT ON TABLE brbl_users.old_users TO brbl_user_rw_role;


--
-- Name: pg_roll_handle_ddl; Type: EVENT TRIGGER; Schema: -; Owner: mark
--

CREATE EVENT TRIGGER pg_roll_handle_ddl ON ddl_command_end
   EXECUTE FUNCTION pgroll.raw_migration();


ALTER EVENT TRIGGER pg_roll_handle_ddl OWNER TO mark;

--
-- Name: pg_roll_handle_drop; Type: EVENT TRIGGER; Schema: -; Owner: mark
--

CREATE EVENT TRIGGER pg_roll_handle_drop ON sql_drop
   EXECUTE FUNCTION pgroll.raw_migration();


ALTER EVENT TRIGGER pg_roll_handle_drop OWNER TO mark;

--
-- PostgreSQL database dump complete
--

\unrestrict Jq5Nh4sHf4aVx9ibh3eaFbRUNHQwLGjPDfLeQ3gjiHMNQock5upNaEmajex5X44

