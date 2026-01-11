--
-- PostgreSQL database dump
--

-- Dumped from database version 17.4 (Homebrew)
-- Dumped by pg_dump version 17.4 (Homebrew)

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
-- Name: brbl_users; Type: SCHEMA; Schema: -; Owner: brbl_admin
--

CREATE SCHEMA brbl_users;


ALTER SCHEMA brbl_users OWNER TO brbl_admin;

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


SET default_tablespace = '';

SET default_table_access_method = heap;

-- ----------------------------------------------------------------------------------------------------------
--
-- Name: companies_t; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--

CREATE TABLE brbl_users.companies_t (
                                   id uuid NOT NULL,
                                   name character varying(64),
                                   created_at timestamp with time zone DEFAULT now() NOT NULL,
                                   updated_at timestamp with time zone DEFAULT now() NOT NULL
);
--
-- Name: companies_t companies_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.companies_t
    ADD CONSTRAINT companies_t_pkey PRIMARY KEY (id);

ALTER TABLE brbl_users.companies_t OWNER TO brbl_admin;

-- ----------------------------------------------------------------------------------------------------------
--
-- Name: customers; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--
CREATE TABLE brbl_users.customers_t (
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
--
-- Name: customers_t customers_t_confirmation_code_key; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.customers_t
    ADD CONSTRAINT customers_t_confirmation_code_key UNIQUE (confirmation_code);
--
-- Name: customers_t customers_t_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.customers_t
    ADD CONSTRAINT customers_t_pkey PRIMARY KEY (id);--
-- Name: customers_t unique_email; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.customers_t
    ADD CONSTRAINT unique_customers_t_email UNIQUE (email);
--
-- Name: customers_t fk_customer_company_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.customers_t
    ADD CONSTRAINT fk_customers_t_company_id FOREIGN KEY (company_id) REFERENCES brbl_users.companies_t(id);
--
-- Name: customers_t fk_customer_profile_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.customers_t
    ADD CONSTRAINT fk_customers_t_profile_id FOREIGN KEY (profile_id) REFERENCES brbl_users.profiles(id);

ALTER TABLE brbl_users.customers_t OWNER TO brbl_admin;

-- ----------------------------------------------------------------------------------------------------------
--
-- Name: profiles; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--
CREATE TABLE brbl_users.profiles (
                                     id uuid NOT NULL, -- logically, corresponds to amalgams.profile_id
                                     surname character varying(36),
                                     given_name character varying(36),
                                     other_languages character varying(23),
                                     created_at timestamp with time zone NOT NULL,
                                     updated_at timestamp with time zone DEFAULT now() NOT NULL,
                                     roles character varying(64)
);
--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);

ALTER TABLE brbl_users.profiles OWNER TO brbl_admin;


-- ----------------------------------------------------------------------------------------------------------
--
-- Name: users; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--
CREATE TABLE brbl_users.users (
                                  id uuid NOT NULL,
                                  platform_code public.platform NOT NULL,
                                  platform_id character varying(36) NOT NULL,
                                  -- customer_id uuid NOT NULL,
                                  country public.country_code NOT NULL,
                                  language public.language_code NOT NULL,
                                  nickname character varying(36),
                                  status brbl_users.user_status DEFAULT 'KNOWN'::brbl_users.user_status NOT NULL,
                                  created_at timestamp with time zone NOT NULL,
                                  updated_at timestamp with time zone NOT NULL
);
--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
--
-- Name: users users_platform_customer_id_unique; Type: CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.users
    ADD CONSTRAINT users_platform_customer_id_unique UNIQUE (platform_code, customer_id, platform_id);
--
-- Name: users fk_customer_id; Type: FK CONSTRAINT; Schema: brbl_users; Owner: brbl_admin
--
ALTER TABLE ONLY brbl_users.users
    ADD CONSTRAINT fk_users_customer_id FOREIGN KEY (customer_id) REFERENCES brbl_users.customers_t(id);

ALTER TABLE brbl_users.users OWNER TO brbl_admin;

-- ----------------------------------------------------------------------------------------------------------
--
-- Name: amalgams; Type: TABLE; Schema: brbl_users; Owner: brbl_admin
--
CREATE TABLE brbl_users.amalgams_t (
    group_id     uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    profile_id   uuid,
    customer_id  uuid,
    created_at  timestamp with time zone NOT NULL,
    updated_at  timestamp with time zone NOT NULL,
    CONSTRAINT unique_group_user_profile_customer
        UNIQUE(group_id, user_id, profile_id, customer_id),
    CONSTRAINT fk_users_user_id FOREIGN KEY(user_id)
        REFERENCES brbl_users.users(id),
    CONSTRAINT fk_users_profile_id FOREIGN KEY (profile_id)
        REFERENCES brbl_users.profiles(id),
    CONSTRAINT fk_users_customer_id FOREIGN KEY (customer_id)
        REFERENCES brbl_users.customers(id)
);

CREATE TABLE brbl_users.amalgams_t (
    group_id     uuid NOT NULL,
    user_id      uuid NOT NULL,
    profile_id   uuid,
    customer_id  uuid,
    created_at  timestamp with time zone NOT NULL,
    updated_at  timestamp with time zone NOT NULL,
    CONSTRAINT uq_group_user_profile_customer UNIQUE(group_id, user_id, profile_id, customer_id),
    CONSTRAINT fk_users_user_id FOREIGN KEY(user_id)
        REFERENCES brbl_users.users(id),
    CONSTRAINT fk_users_profile_id FOREIGN KEY (profile_id)
        REFERENCES brbl_users.profiles(id),
    CONSTRAINT fk_users_customer_id FOREIGN KEY (customer_id)
        REFERENCES brbl_users.customers_t(id)
);

-- ----------------------------------------------------------------------------------------------------------
--
-- Name: SCHEMA brbl_users; Type: ACL; Schema: -; Owner: brbl_admin
--
GRANT USAGE ON SCHEMA brbl_users TO brbl_user_rw_role;
--
-- Name: TABLE profiles; Type: ACL; Schema: brbl_users; Owner: brbl_admin
--
GRANT SELECT,INSERT ON TABLE brbl_users.profiles TO brbl_user_rw_role;
--
-- Name: TABLE users; Type: ACL; Schema: brbl_users; Owner: brbl_admin
--
GRANT SELECT,INSERT ON TABLE brbl_users.users TO brbl_user_rw_role;
--
-- PostgreSQL database dump complete
--

