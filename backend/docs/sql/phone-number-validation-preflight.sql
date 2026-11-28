-- Read-only inventory for the approved 10-digit Vietnamese phone format.
-- Connect to the exact reviewed database first; this script performs no writes.
SET NOCOUNT ON;
SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name, DB_ID() AS database_id,
       state_desc AS database_state
FROM sys.databases WHERE database_id = DB_ID();

SELECT s.name AS schema_name, t.name AS table_name, c.name AS column_name,
       ty.name AS data_type, c.max_length, c.is_nullable
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE (t.name = 'users' AND c.name = 'phone')
   OR (t.name = 'consultation_request' AND c.name = 'phone');

-- Length and BIN2 range together enforce ten ASCII code units beginning with ASCII zero.
-- DATALENGTH catches trailing spaces (LEN alone ignores them). Nullability is preserved.
SELECT 'users' AS table_name,
       COUNT_BIG(*) AS total_rows,
       SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) AS null_rows,
       SUM(CASE WHEN phone IS NOT NULL AND LTRIM(RTRIM(phone)) = '' THEN 1 ELSE 0 END) AS blank_rows,
       SUM(CASE WHEN phone IS NOT NULL AND DATALENGTH(phone) = 10
                     AND phone COLLATE Latin1_General_100_BIN2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
                THEN 1 ELSE 0 END) AS valid_rows,
       SUM(CASE WHEN phone IS NOT NULL AND NOT (DATALENGTH(phone) = 10
                     AND phone COLLATE Latin1_General_100_BIN2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
                THEN 1 ELSE 0 END) AS invalid_rows
FROM dbo.users;

SELECT 'consultation_request' AS table_name,
       COUNT_BIG(*) AS total_rows,
       SUM(CASE WHEN phone IS NULL THEN 1 ELSE 0 END) AS null_rows,
       SUM(CASE WHEN phone IS NOT NULL AND LTRIM(RTRIM(phone)) = '' THEN 1 ELSE 0 END) AS blank_rows,
       SUM(CASE WHEN phone IS NOT NULL AND DATALENGTH(phone) = 20
                     AND phone COLLATE Latin1_General_100_BIN2 LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
                THEN 1 ELSE 0 END) AS valid_rows,
       SUM(CASE WHEN phone IS NOT NULL AND NOT (DATALENGTH(phone) = 20
                     AND phone COLLATE Latin1_General_100_BIN2 LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
                THEN 1 ELSE 0 END) AS invalid_rows
FROM dbo.consultation_request;

-- Show identifiers and masked invalid values only; no names or other contact data.
SELECT 'users' AS table_name, id AS record_id,
       CASE WHEN LEN(CONVERT(varchar(20), phone)) > 2
            THEN LEFT(CONVERT(varchar(20), phone), 2) + REPLICATE('*', CASE WHEN LEN(CONVERT(varchar(20), phone)) > 3 THEN LEN(CONVERT(varchar(20), phone)) - 3 ELSE 0 END)
                 + RIGHT(CONVERT(varchar(20), phone), 1)
            ELSE REPLICATE('*', LEN(CONVERT(varchar(20), phone))) END AS masked_phone
FROM dbo.users
WHERE phone IS NOT NULL AND NOT (DATALENGTH(phone) = 10
  AND phone COLLATE Latin1_General_100_BIN2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
UNION ALL
SELECT 'consultation_request', id,
       CASE WHEN LEN(phone) > 2 THEN LEFT(phone, 2) + REPLICATE(N'*', CASE WHEN LEN(phone) > 3 THEN LEN(phone) - 3 ELSE 0 END) + RIGHT(phone, 1)
            ELSE REPLICATE(N'*', LEN(phone)) END
FROM dbo.consultation_request
WHERE phone IS NOT NULL AND NOT (DATALENGTH(phone) = 20
  AND phone COLLATE Latin1_General_100_BIN2 LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
ORDER BY table_name, record_id;

-- Duplicate valid numbers are intentionally allowed; this is an aggregate only.
SELECT 'users' AS table_name, COUNT_BIG(*) AS duplicate_phone_groups
FROM (SELECT phone FROM dbo.users WHERE phone IS NOT NULL
      AND DATALENGTH(phone) = 10
      AND phone COLLATE Latin1_General_100_BIN2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
      GROUP BY phone HAVING COUNT_BIG(*) > 1) d
UNION ALL
SELECT 'consultation_request', COUNT_BIG(*)
FROM (SELECT phone FROM dbo.consultation_request WHERE phone IS NOT NULL
      AND DATALENGTH(phone) = 20
      AND phone COLLATE Latin1_General_100_BIN2 LIKE N'0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
      GROUP BY phone HAVING COUNT_BIG(*) > 1) d;
