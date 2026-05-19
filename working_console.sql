UPDATE brbl_users.users
SET nickname = 'old_' || nickname
WHERE nickname IS NOT NULL;

UPDATE brbl_users.users
SET nickname = 'old_'
WHERE nickname IS NULL;

UPDATE brbl_users.profiles
SET surname = 'old_' || surname
WHERE surname IS NOT NULL;

UPDATE brbl_users.customers
SET email = 'old_' || email
WHERE email IS NOT NULL;

SELECT *
FROM brbl_users.profiles
WHERE surname LIKE 'old_%'; -- the new, generated data

SELECT
FROM brbl_users.users
WHERE nickname NOT LIKE 'old_%';

SELECT a.group_id,
       a.user_id,
       c.name,
       u.platform_code AS code,
       u.platform_id,
       p.given_name,
       p.surname,
       s.email
FROM brbl_users.amalgams a
         INNER JOIN brbl_users.companies c ON c.id = a.claimant_id
         INNER JOIN brbl_users.users u ON u.id = a.user_id
         LEFT JOIN brbl_users.profiles p ON p.id = a.profile_id
         LEFT JOIN brbl_users.customers s ON s.id = a.customer_id
WHERE c.name NOT LIKE '%_OLD'
ORDER BY a.group_id, a.user_id;

SELECT * FROM brbl_users.amalgams
    WHERE user_id IN
('019d2055-9235-73cc-8c75-2cb97dbbc285',
'019d2553-bdcc-7433-b100-b4fb88d92b0a',
'019d5919-85ba-72d7-97ce-5a8a38aba4f6');

-- company_id = 019d0375-fa2c-7e1d-b9a2-14411de8cfc0
DELETE
FROM brbl_logic.sessions
WHERE created_at >= '2026-01-01';

SELECT count(*) FROM brbl_logic.sessions;

CREATE TYPE brbl_logic.node_type AS enum ('PRESENT_MULTI','PROCESS_MULTI','END_OF_CHAT','REQUEST_INPUT','PROCESS_INPUT','SEND_MESSAGE');
ALTER TYPE brbl_logic.node_type OWNER TO brbl_admin;
ALTER TABLE brbl_logic.nodes ADD COLUMN ntype node_type;
GRANT SELECT ON brbl_logic.nodes TO brbl_logic_read_role;


-- ALTER TABLE nodes DROP COLUMN ntype;
-- DROP TYPE brbl_logic.node_type;

--     PresentMulti    (false), // 3
--     ProcessMulti    (true),  // 4
--     EndOfChat       (false), // 5
--     RequestInput    (false), // 6
--     ProcessInput    (true),  // 7
--     SendMessage     (false)  // 8

-- SELECT DISTINCT (type) FROM brbl_logic.nodes;
-- UPDATE nodes
-- SET ntype =
--         CASE
--             WHEN type = 3 THEN 'PRESENT_MULTI'::node_type
--             WHEN type = 4 THEN 'PROCESS_MULTI'::node_type
--             WHEN type = 5 THEN   'END_OF_CHAT'::node_type
--             WHEN type = 6 THEN 'REQUEST_INPUT'::node_type
--             WHEN type = 7 THEN 'PROCESS_INPUT'::node_type
--             WHEN type = 8 THEN  'SEND_MESSAGE'::node_type
--         END
-- WHERE type > 2;

-- ALTER TABLE brbl_logic.nodes RENAME COLUMN type TO num_type; -- run tests to see if anything breaks
-- then drop it and rename ntype to type.

ALTER TABLE brbl_logic.nodes RENAME COLUMN ntype TO type ;

-- old, unused data
SELECT * FROM brbl_logs.messages_mo_prcd ; -- deleted
SELECT * FROM brbl_logs.messages_mo ;      -- deleted
SELECT * FROM brbl_logs.messages_mt ;      -- deleted
SELECT * FROM brbl_logs.messages_mt_dlvr ; -- deleted

SELECT COUNT(*) FROM nodes;    --> 369
SELECT COUNT(*) FROM keywords; -->   8
SELECT COUNT(*) FROM scripts;  --> 102

SELECT * FROM nodes WHERE text ILIKE '%color%';
    --> 89eddcb8-7fe5-4cd1-b18b-78858f0789fb 'What is your favorite color? 1) red 2) blue 3) flort'
    -- WHERE id = '0fc4ef6c-082f-4e90-b2f4-e14dbac78623';
        --> the 3 types of food quiz

SELECT * FROM keywords WHERE script_id = '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'; -- 5 entries, all legit incl. route_id
--> route_id is 715e3d1d-6a64-41cd-a3fa-fe12567b38ef

SELECT * FROM routes WHERE id='715e3d1d-6a64-41cd-a3fa-fe12567b38ef';
SELECT * FROM companies WHERE id ='8410c710-6986-e350-d3d5-28428a640e5f'; --> MicroCorp_OLD, status: ACTIVE
SELECT * FROM companies;


SELECT id, label, type, text, created_at
FROM nodes
-- WHERE type = 'PRESENT_MULTI'
--     AND label ilike '%food%'
-- WHERE id = '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'
-- WHERE id = 'b48d36ce-2512-4ee0-a9b9-b743d72e95e9'
WHERE id = '525028ae-0a33-4c80-a22f-868f77bb9531'
ORDER BY created_at;

SELECT * FROM edges where src = '525028ae-0a33-4c80-a22f-868f77bb9531';

SELECT *
FROM keywords
WHERE id = '2cd3acf2-02ce-4330-a1ae-09e75f368fc4';



-- produce the graph lists for the following conversations. Delete everything else (not including fave foods quiz.)
-- '89eddcb8-7fe5-4cd1-b18b-78858f0789fb'	--> ColorQuiz (will include ShapeQuiz, UtensilQuiz, and StoogeQuiz.)
    -- Nodes
SELECT * FROM nodes WHERE id IN (
              '0b2861b6-a16a-4197-910a-158610967dd9',
              '1bda7846-31b4-4983-92ec-161eba46c758',
              '23f00a69-55b7-4c29-b777-5c278b7088ed',
              '385a1f99-d844-42e6-9fa3-a0e3a116757d',
              '3f467ee3-4874-4ad1-82b1-99be06f575ab',
              '89eddcb8-7fe5-4cd1-b18b-78858f0789fb',
              '95441b9a-3636-4f45-bd0a-ec35e84dd5f7',
              'b48d36ce-2512-4ee0-a9b9-b743d72e95e9',
              'f9420f0c-81ca-4f9a-b1d4-7e25fd280399'
    );
    -- Edges
SELECT * FROM edges WHERE id IN (
              '190dc5a3-fab2-49c5-b475-242d8e06292f',
              '1a5aabc0-74f2-4faf-a1fa-783e412d4db9',
              '31ec69f2-c618-492a-aaa7-68bb6a4475f5',
              '43e373db-4e32-42fa-9039-12e69aa30223',
              '49a0e06a-fff6-4bbc-91f7-fcda4b800cc4',
              '4bce9e23-2dc4-42d9-aea9-94744a74d005',
              '4bce9e23-2dc4-42d9-aea9-94744a74d005',
              '65078637-9862-4682-8bbe-0a52ed29bf6b',
              '6da7f442-71f2-431c-8e9c-3d758c5b18de',
              '731d8193-c8f8-4ce0-ac90-026371bdc927',
              '77ff5c56-ac17-4410-8cf9-439b34e7c587',
              'b71f650a-e48a-4eb2-9909-33327e99050f',
              'd9d9d89b-3047-4b18-8c97-5fb870fc1ced',
              'e3817413-e1fa-4d60-b049-a1c0083ebbd2',
              'e3c895c7-9318-4597-b99d-8b647f1da409',
              'ed53f6d3-3e66-4321-9ba7-ad3c36e79eb4',
              'fb487221-d767-43fc-a17f-0b0cd89a7e78',
              'fee09a2a-5595-43a9-8228-72182789800e'
    );

--> BadPeople
--         Nodes:
SELECT * FROM nodes WHERE id IN (
        '525028ae-0a33-4c80-a22f-868f77bb9531',
        'ae28c0e3-9303-4807-979f-694bc9981dd7',
        'cf72ce06-50fc-4bf1-852b-dbdbd9f97f66',
        'aec8789f-546f-42c2-b1d5-c80bd10014f0'
    );
--         Edges:
SELECT * FROM edges WHERE id IN (
        '83408cce-85a9-4824-b622-651c6839f399',
        '2361468c-571d-43e1-a5cc-a5580841253c',
        '4aaf8877-f0dc-4182-94f9-86f05e87de3a',
        '053c7425-dfd9-4c32-b305-a15b50453274',
        'f6c08bf6-a984-4619-97be-3bb2526ba81d'
    );

--> 0:FoodQuiz
        -- Nodes
SELECT * FROM nodes WHERE id IN (
        '0fc4ef6c-082f-4e90-b2f4-e14dbac78623',
        'ed0fbc0d-a3e3-40b4-91a4-9be24803e2db',
        '369d49d1-6c17-4afe-a6c1-ba01026798f3',
        'eff5596e-c39a-432c-8dd5-7fd596d8182d',
        '8da657ae-32d2-4fc1-8d27-2cfcd48858f6',
        'ec4bb0f2-124f-4755-8331-40f64ce24940',
        '35b8b472-fe8c-4201-895c-d2b128fdde32',
        'a77300e2-d7a7-40ea-bd0a-0d957a3ff5b5',
        '1b76e670-f683-4863-a30d-9262c3664a82'
    );
        -- Edges
SELECT * FROM edges WHERE id IN (
        '056e6fb7-05a2-4646-b40c-851c9c6d1081',
        '341df59c-b544-4435-9110-fa7fd2673eb8',
        '83d2e3e6-fa99-4c08-993b-74f43ff19e63',
        'a2810868-6db9-428c-9336-809fc46ad15f',
        '20ba3097-2f74-4ce5-a4e5-102dcb3a50dc',
        '9d0ceae7-cf8d-4abb-8d24-477d59883333',
        '631f5998-aa30-4450-9f6c-ddeba77ffb9f',
        '041cd7cb-5fbf-41ce-97d1-4708d70f1958',
        '9101f6c0-3329-4284-9b8c-ae635360431a',
        '8e52ae5f-e2e8-4e1d-af93-4a8f6ff143eb',
        '01eff2e7-d605-4cb3-a498-9c93ad2d32c0',
        'c5de2fd4-f89b-45c0-881f-aa5ab1b5326c',
        'af8f80db-79bf-43c1-9362-1f330da7df08',
        'ffc67bd4-51fd-4fb4-85f5-11bf5a5f00f8',
        '055e7de8-740e-4e0d-b092-c9cdc2517728',
        'c1a8c501-7886-48e4-8b50-c854c26cbd06',
        '06cbb51f-7ac5-47a4-9ccf-eb6b93cf3d52'
    );


-- Practice delete/reload:
DELETE FROM nodes WHERE id IN (
        '525028ae-0a33-4c80-a22f-868f77bb9531',
        'ae28c0e3-9303-4807-979f-694bc9981dd7',
        'cf72ce06-50fc-4bf1-852b-dbdbd9f97f66',
        'aec8789f-546f-42c2-b1d5-c80bd10014f0'
    );
--         Edges:
DELETE FROM edges WHERE id IN (
        '83408cce-85a9-4824-b622-651c6839f399',
        '2361468c-571d-43e1-a5cc-a5580841253c',
        '4aaf8877-f0dc-4182-94f9-86f05e87de3a',
        '053c7425-dfd9-4c32-b305-a15b50453274',
        'f6c08bf6-a984-4619-97be-3bb2526ba81d'
    );

select * from scripts;

-- Starting data cleanup
BEGIN TRANSACTION ;
    DELETE FROM campaign_users WHERE delivered IN ('PENDING', 'SENT');
    DELETE FROM push_campaigns WHERE created_at > '1980-01-01';
    DELETE FROM scripts WHERE created_at > '1980-01-01';
    DELETE FROM keywords WHERE created_at > '1980-01-01';
    DELETE FROM routes WHERE created_at > '1980-01-01';
COMMIT;

DELETE FROM sessions WHERE created_at > '1980-01-01';

