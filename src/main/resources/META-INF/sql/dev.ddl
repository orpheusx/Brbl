-- In-development DDL...

CREATE TYPE language_code AS ENUM ('ENG', 'SPA', 'FRA', 'HAT', 'CMN', 'YUE', 'POR', 'RUS');
CREATE TYPE country_code  AS ENUM ('US', 'CA', 'MX');
CREATE TYPE platform      AS ENUM ('S', 'B', 'W', 'F');

-- FYI:
-- ALTER TYPE enum_type ADD VALUE 'new_value'; -- appends to list
-- ALTER TYPE enum_type ADD VALUE 'new_value' BEFORE 'old_value';
-- ALTER TYPE enum_type ADD VALUE 'new_value' AFTER 'old_value';

CREATE USER brbl_admin WITH ENCRYPTED PASSWORD 'brbl_admin';
CREATE DATABASE brbl_db_dev WITH OWNER brbl_admin;

-- ...then exit and reconnect `psql -U brbl_admin -d brbl_db_dev`
-- I don't think this is required. The schemas and tables will end up being owned by brbl_admin even if
-- the superuser is the executing the DDL.

-- Note: To display the enum types in psql use a variant of the describe command:
--    \dT+ <name-of-enum-type>
-- To list all the types we created, simply use:
--    \dT



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
CREATE SCHEMA IF NOT EXISTS brbl_users AUTHORIZATION brbl_admin ;

CREATE TABLE brbl_users.users (
    group_id        UUID NOT NULL,
    platform_id     VARCHAR(36) NOT NULL,
    platform_code   PLATFORM NOT NULL, -- See Platform type
    country         country_code NOT NULL,
    language        language_code NOT NULL,
    nickname        VARCHAR(36), -- privacy preserving but friendly
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (platform_id, platform_code)
);
-- Probably only the platform_id needs to be in the primary key
-- The group_id will act as the grouping value. If a user joins from a
--   different platform they will have the same group_id but distinct
--   platform_id and code. We find all platforms for the user via this
--   column.

-- INSERT INTO brbl_users.users VALUES
-- (
--     '7213f19f-e28d-40a8-856c-c8478bedde61',
--     '17817299468',
--     'S',
--     'US',
--     'ENG',
--     'BuddyBoy'
-- );

CREATE TABLE brbl_users.profiles (
    group_id        UUID PRIMARY KEY,
    surname         VARCHAR(36),
    given_name      VARCHAR(36),
    other_languages VARCHAR(23), -- room for six more
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

--INSERT INTO brbl_users.users VALUES ('7213f19f-e28d-40a8-856c-c8478bedde61', 'xyz123', 'B', 'US', 'ENG', 'Friendo' );

--  It will be expensive to connect two users after the fact. The ideal scenario
-- would be to use a link to invite the user to join on a different platform.
-- In that case we can create the second User record already knowing the group_id.

-- ============================ ACCESS CONTROL ====================================================
--  For non-login roles, follow the pattern: <schema>_<grant_level_for_all_tables_in_schema>
--  Remember, in Postgres, users are really an alias for role.

-- brbl_admin  --> not super-user but has full access to all our schemas/tables
GRANT ALL ON ALL TABLES IN SCHEMA "brbl_users", "brbl_logs" TO brbl_admin;

-- brbl_logs_write_role --> (no login) write to the brbl_logs.message_* tables
CREATE ROLE brbl_logs_write_role;
GRANT USAGE ON SCHEMA brbl_logs TO brbl_logs_write_role;
GRANT INSERT ON
    brbl_logs.messages_mo,
    brbl_logs.messages_mo_prcd,
    brbl_logs.messages_mt,
    brbl_logs.messages_mt_dlvr
TO brbl_logs_write_role;

-- brbl_user_rw_role --> (no login) read and write access to brbl_users.*
CREATE ROLE brbl_user_rw_role;
    GRANT USAGE ON SCHEMA brbl_users TO brbl_user_rw_role;
    GRANT INSERT, SELECT ON
        brbl_users.users,
        brbl_users.profiles
    TO brbl_user_rw_role;

-- brbl_rcvr --> (login) granted brbl_logs_write_role only
CREATE ROLE brbl_rcvr LOGIN ENCRYPTED PASSWORD 'brbl_rcvr';
GRANT brbl_logs_write_role TO brbl_rcvr;

-- brbl_sndr --> (login) granted brbl_logs_write_role only
CREATE ROLE brbl_sndr LOGIN ENCRYPTED PASSWORD 'brbl_sndr';
GRANT brbl_logs_write_role TO brbl_sndr;

brbl_operator --> (login) granted both brbl_logs_write_role and brbl_user_rw_role
CREATE ROLE brbl_operator LOGIN ENCRYPTED PASSWORD 'brbl_operator';
GRANT brbl_logs_write_role, brbl_user_rw_role TO brbl_operator;

-- To avoid need to specify the schema when mucking about in psql...
SET search_path TO brbl_logs, brbl_users, brbl_logic



CREATE SCHEMA IF NOT EXISTS brbl_logic AUTHORIZATION brbl_admin ;

CREATE TABLE brbl_logic.scripts (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    text        VARCHAR(255),       --> SMS is limited to 160 chars but other platform have higher limits.
    type        SMALLINT NOT NULL,  --> see ScriptType enum for meaning.
    label       VARCHAR(32)         --> the name given to the script element in a UI
);

CREATE TABLE brbl_logic.edges (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    match_text      VARCHAR(128),       --> the text that must be matched to direct the conversation to the dst script
    response_text   VARCHAR(255),       --> the text emitted when the edge is selected.
    src             UUID NOT NULL,      --> FK to scripts table
    dst             UUID NOT NULL      --> FK to scripts table
    -- CONSTRAINT fk_script_src
    --     FOREIGN KEY(id) REFERENCES brbl_logic.scripts(id),
    -- CONSTRAINT fk_script_dst
    --     FOREIGN KEY(id) REFERENCES brbl_logic.scripts(id)
);

-- Script 1
INSERT INTO brbl_logic.scripts VALUES('89eddcb8-7fe5-4cd1-b18b-78858f0789fb', NOW(),
    'What is your favorite color? 1) red 2) blue 3) flort', 4, 'ColorQuiz') RETURNING *;
-- Script 2
INSERT INTO brbl_logic.scripts VALUES('2ed4ceed-a229-4e82-ab89-668a15835058', NOW(),
    'Oops, that is not one of the options. Try again with one of the listed numbers or say "change topic" to start talking about something else.', 5, 'EvaluateColorAnswer') RETURNING *;
-- Script 3
INSERT INTO brbl_logic.scripts VALUES('f9420f0c-81ca-4f9a-b1d4-7e25fd280399', NOW(),
    'That is all. Bye.', 1, 'EndOfConversation') RETURNING *;

INSERT INTO brbl_logic.edges VALUES('d9d9d89b-3047-4b18-8c97-5fb870fc1ced', NOW(), '1|red', 'Red is the color of life.',
    '89eddcb8-7fe5-4cd1-b18b-78858f0789fb', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('fee09a2a-5595-43a9-8228-72182789800e', NOW(), '2|blue', 'Blue is my fave, as well.',
    '89eddcb8-7fe5-4cd1-b18b-78858f0789fb', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('49a0e06a-fff6-4bbc-91f7-fcda4b800cc4', NOW(), '3|flort', 'Flort is for the cool kids.',
    '89eddcb8-7fe5-4cd1-b18b-78858f0789fb', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;

-- brbl_admin  --> not super-user but has full access to all our schemas/tables
GRANT ALL ON ALL TABLES IN SCHEMA "brbl_users", "brbl_logs", "brbl_logic" TO brbl_admin;

-- brbl_logic_read_role --> (no login) read all the brbl_logs.message_* tables
CREATE ROLE brbl_logic_read_role ;
GRANT USAGE ON SCHEMA brbl_logic TO brbl_logic_read_role ;
GRANT SELECT ON brbl_logic.scripts TO brbl_logic_read_role ;
GRANT SELECT ON brbl_logic.edges TO brbl_logic_read_role ;

GRANT brbl_logic_read_role TO brbl_operator;

-- Query to assemble the a Script and the ids of the Scripts to which it is chained directly.
SELECT
    s.id, s.label, s.text,
    e.match_text, e.response_text, e.dst
FROM
    brbl_logic.scripts s
INNER JOIN
    brbl_logic.edges e ON s.id = e.src
WHERE
    s.id = '89eddcb8-7fe5-4cd1-b18b-78858f0789fb';


-- Query to find the id of the initial Script per platform and keyword, including default.
CREATE TABLE brbl_logic.keywords (
    id          UUID NOT NULL UNIQUE,
    pattern     VARCHAR(128),   --> the default entry likely has no value so this can't be NOT NULL
    platform    platform,
    script_id   UUID,
    is_default  BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_script_id
        FOREIGN KEY(script_id)
            REFERENCES brbl_logic.scripts(id),
    CONSTRAINT unique_pattern_platform
        UNIQUE(pattern, platform)
);

INSERT INTO brbl_logic.keywords VALUES(
    gen_random_uuid(),
    'FOO',
    'S'::Platform,
    '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'::UUID,
    FALSE
);

-- Query to populate a keyword cache:
SELECT
    pattern, script_id
FROM
    brbl_logic.keywords
WHERE
    platform = 'S'
    AND (
    pattern = ?
    OR
    is_default IS TRUE
    );

GRANT SELECT ON brbl_logic.keywords TO brbl_logic_read_role ;

-- I think we could leave off the WHERE clause and get all the Scripts that have child nodes. Maybe add ORDER BY s.id.
-- That said this could be a lot of data over time.

WITH RECURSIVE c AS (
    SELECT '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'::UUID AS parent_id
    UNION ALL
    SELECT e.dst
    FROM brbl_logic.edges AS e
        JOIN c ON (c.parent_id = e.src)
)
SELECT parent_id FROM c;

-- Need to add some data to really see if the above query does what we want...
INSERT INTO brbl_logic.scripts VALUES('b48d36ce-2512-4ee0-a9b9-b743d72e95e9', NOW(),
    'Another question. What is your fave shape: 1) triangle, 2) square or 3) circle', 4, 'ShapeQuiz') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('190dc5a3-fab2-49c5-b475-242d8e06292f', NOW(), '1|triangle', 'Triangles are so pointy!',
    'b48d36ce-2512-4ee0-a9b9-b743d72e95e9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('1a5aabc0-74f2-4faf-a1fa-783e412d4db9', NOW(), '2|square', 'Squares are for squares!',
    'b48d36ce-2512-4ee0-a9b9-b743d72e95e9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('6da7f442-71f2-431c-8e9c-3d758c5b18de', NOW(), '3|circle', 'Circles go round and round!',
    'b48d36ce-2512-4ee0-a9b9-b743d72e95e9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;

INSERT INTO brbl_logic.scripts VALUES('385a1f99-d844-42e6-9fa3-a0e3a116757d', NOW(),
    'Second question. What is your fave utensil: 1) fork, 2) knife or 3) spoon', 4, 'UtensilQuiz') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('e3c895c7-9318-4597-b99d-8b647f1da409', NOW(), '1|fork', 'Good for stabbing your food!',
    '385a1f99-d844-42e6-9fa3-a0e3a116757d', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('731d8193-c8f8-4ce0-ac90-026371bdc927', NOW(), '2|knife', 'Cut it into little pieces!',
    '385a1f99-d844-42e6-9fa3-a0e3a116757d', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('43e373db-4e32-42fa-9039-12e69aa30223', NOW(), '3|spoon', 'I guess you like soup!',
    '385a1f99-d844-42e6-9fa3-a0e3a116757d', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;

INSERT INTO brbl_logic.scripts VALUES('0b2861b6-a16a-4197-910a-158610967dd9', NOW(),
    'Third question. Which is the best third Stooge: 1) Shemp, 2) Curly or 3) Iggy', 4, 'StoogeQuiz') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('b71f650a-e48a-4eb2-9909-33327e99050f', NOW(), '1|Shemp', 'Shemp was fire!',
    '0b2861b6-a16a-4197-910a-158610967dd9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('e3817413-e1fa-4d60-b049-a1c0083ebbd2', NOW(), '2|Curly', 'Curly was dope!',
    '0b2861b6-a16a-4197-910a-158610967dd9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;
INSERT INTO brbl_logic.edges VALUES('fb487221-d767-43fc-a17f-0b0cd89a7e78', NOW(), '3|Iggy', 'Oops, wrong Stooges!',
    '0b2861b6-a16a-4197-910a-158610967dd9', 'f9420f0c-81ca-4f9a-b1d4-7e25fd280399') RETURNING *;

-- Returns the id's of all the edges descended from the Script row with the given script_id
WITH RECURSIVE c AS (
    SELECT '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'::UUID AS script_id
    UNION ALL
    SELECT e.dst
    FROM brbl_logic.edges AS e
        JOIN c ON (c.script_id = e.src)
) SELECT script_id FROM c;

-- Can we pull in all the needed columns?
WITH RECURSIVE c AS (
    SELECT '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'::UUID AS script_id
    UNION ALL
    SELECT e.dst
    FROM brbl_logic.edges AS e
        JOIN c ON (c.script_id = e.src)
) SELECT
    s.id, s.label, s.text,
    e.match_text, e.response_text, e.dst
  FROM
    brbl_logic.scripts s
  INNER JOIN
    brbl_logic.edges e ON s.id = e.src
  WHERE
    s.id IN (SELECT DISTINCT(c.script_id) FROM c);

-- YES!!! This works but it doesn't include the final Script because there aren't any rows in the EDGES table that reference it.
-- This is a problem...

-- Removed the NOT NULL constraint on the dst column for brbl_logic.edges and added a row for the final script that has null as it's dst value:

INSERT INTO brbl_logic.edges VALUES(gen_random_uuid(), NOW(), 'NOOP', 'NOOP',
    'f9420f0c-81ca-4f9a-b1d4-7e25fd280399', null) RETURNING *;

-- Now the last script in the graph is included by the recursive query.

-- Next problem: supporting cycles...

UPDATE brbl_logic.edges e
    SET dst = '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'
    WHERE e.id = '4bce9e23-2dc4-42d9-aea9-94744a74d005';

WITH RECURSIVE cte AS (
        SELECT '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'::UUID AS script_id
        UNION ALL
        SELECT e.dst
        FROM brbl_logic.edges AS e
            JOIN cte ON (cte.script_id = e.src)
    ) CYCLE script_id SET is_cycle USING path
    SELECT
        s.id, s.created_at, s.text, s.type, s.label,
        e.id, e.created_at, e.match_text, e.response_text, e.src, e.dst
    FROM
        brbl_logic.scripts s
    INNER JOIN
        brbl_logic.edges e ON s.id = e.src
    WHERE
        s.id IN (SELECT DISTINCT(cte.script_id) FROM cte);

--> The CYCLE-SET-USING feature tracks the reference value to check for duplicates, avoiding endless loops.


