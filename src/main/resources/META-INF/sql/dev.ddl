-- In-development DDL...

CREATE TYPE language_code AS ENUM ('en', 'es', 'fr');
CREATE TYPE country_code AS ENUM ('us', 'ca', 'mx');
CREATE TYPE platform AS ENUM ('sms', 'brb', 'wap', 'fbm');

CREATE USER brbl_admin WITH ENCRYPTED PASSWORD 'brbl_admin';
CREATE DATABASE brbl_db_dev WITH OWNER brbl_admin;

then exit and `psql -U brbl_admin -d brbl_db_dev`

CREATE SCHEMA IF NOT EXISTS brbl_logs AUTHORIZATION brbl_admin ;

-- Rcvr writes the full Message with its UUID, the timestamp, and the rest. Before or after enqueuing?
CREATE TABLE brbl_logs.messages_mo (
    id          UUID NOT NULL,
    rcvd_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    _from       VARCHAR(15) NOT NULL,
    _to         VARCHAR(15) NOT NULL,   --> how long are non-SMS (WhatsApp, FB Messenger, etc) identifiers?
    _text       VARCHAR(2000) NOT NULL  --> should match the MT length
);

-- Operator writes an abbreviated row with just the UUID of the Message, the Session UUID, and the UUID of the handling script.
CREATE TABLE brbl_logs.messages_mo_prcd (
    id          UUID NOT NULL,
    prcd_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    session_id  UUID NOT NULL,
    script_id   UUID NOT NULL
);

-- To join the two, showing including messages that were received but not processed:
-- SELECT
--     rcvd.id, prcd.session_id, prcd.script_id, rcvd._text
-- FROM
--     brbl_logs.messages_mo AS rcvd
-- LEFT JOIN
--     brbl_logs.messages_mo_prcd AS prcd
-- ON
--     rcvd.id = prcd.id;

-- For each outgoing Message:
-- Operator writes the full Message immediately after enqueuing it.
CREATE TABLE brbl_logs.messages_mt (
    id          UUID NOT NULL,
    sent_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    _from       VARCHAR(15) NOT NULL,
    _to         VARCHAR(15) NOT NULL,   --> how long are non-SMS (WhatsApp, FB Messenger, etc) identifiers?
    _text       VARCHAR(2000) NOT NULL,  --> should match the MO length
    session_id  UUID NOT NULL,
    script_id   UUID NOT NULL
);

-- For each delivered Message acked by the 3rd party gateway:
-- Sndr writes an abbreviated row with just the UUID and the delivery timestamp.
CREATE TABLE brbl_logs.messages_mt_dlvr (
    id          UUID NOT NULL,
    dlvr_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

-- SELECT sent.id, sent_at, dlvr_at, sent._text, sent.session_id, sent.script_id
-- FROM
--     brbl_logs.messages_mt AS sent
-- LEFT JOIN
--     brbl_logs.messages_mt_dlvr AS dlvr
-- ON
--     sent.id = dlvr.id
-- ;

CREATE USER brbl_logs_writer WITH ENCRYPTED PASSWORD 'brbl_logs_writer';

GRANT USAGE ON SCHEMA brbl_logs TO brbl_logs_writer;
GRANT INSERT on brbl_logs.messages_mo      TO brbl_logs_writer;
GRANT INSERT on brbl_logs.messages_mo_prcd TO brbl_logs_writer;
GRANT INSERT on brbl_logs.messages_mt      TO brbl_logs_writer;
GRANT INSERT on brbl_logs.messages_mt_dlvr TO brbl_logs_writer;

-- TODO add a script context foreign key column once we have worked out script persistence...

// TODO add COMMENT ON TABLE  brbl_logs.<each-table> IS '';
// TODO add COMMENT ON COLUMN brbl_logs.<each-table>.<each-column>
