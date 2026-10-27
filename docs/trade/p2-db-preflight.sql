-- P2 Stage B 수동 DB 사전점검 템플릿
-- PostgreSQL psql에서 승인된 담당자가 읽기 전용으로 실행한다.
-- 저장소의 .env나 애플리케이션 접속값을 자동으로 읽지 않는다.
-- 실행 전 아래 두 값을 승인된 대상으로 직접 바꾼다.
\set target_database 'REPLACE_WITH_APPROVED_DATABASE'
\set target_schema 'REPLACE_WITH_APPROVED_SCHEMA'
\set ON_ERROR_STOP on

SELECT current_database() = :'target_database' AS target_database_matches,
       :'target_schema' <> 'REPLACE_WITH_APPROVED_SCHEMA' AS target_schema_supplied,
       current_database() AS connected_database,
       current_user AS connected_user,
       :'target_schema' AS target_schema
\gset

\if :target_database_matches
\else
  \echo '대상 DB가 입력값과 다릅니다. 아무 점검도 실행하지 않습니다.'
  \quit
\endif

\if :target_schema_supplied
\else
  \echo '대상 스키마가 입력되지 않았습니다. 아무 점검도 실행하지 않습니다.'
  \quit
\endif

BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SET LOCAL lock_timeout = '2s';

-- 1단계: 카탈로그상 객체 존재와 점검 계정 권한을 분리한다.
-- pg_catalog 조회 자체가 실패하면 부재로 판단하지 않고 STRUCTURE_UNCONFIRMED로 기록한다.
SELECT :'target_schema' AS target_schema,
       CASE WHEN namespace_info.oid IS NULL
         THEN 'SCHEMA_NOT_FOUND_IN_CATALOG'
         ELSE 'SCHEMA_PRESENT_IN_CATALOG'
       END AS catalog_state,
       CASE WHEN namespace_info.oid IS NULL THEN NULL
         ELSE has_schema_privilege(namespace_info.oid, 'USAGE')
       END AS usage_granted,
       information_schema_info.schema_name IS NOT NULL AS information_schema_visible
FROM (SELECT 1) seed
LEFT JOIN pg_namespace namespace_info
  ON namespace_info.nspname = :'target_schema'
LEFT JOIN information_schema.schemata information_schema_info
  ON information_schema_info.schema_name = :'target_schema';

SELECT expected.object_name,
       CASE
         WHEN namespace_info.oid IS NULL THEN 'SCHEMA_NOT_FOUND_IN_CATALOG'
         WHEN relation.oid IS NULL THEN 'OBJECT_NOT_FOUND_IN_CATALOG'
         ELSE 'OBJECT_PRESENT_IN_CATALOG'
       END AS catalog_state,
       CASE WHEN namespace_info.oid IS NULL THEN NULL
         ELSE has_schema_privilege(namespace_info.oid, 'USAGE')
       END AS schema_usage_granted,
       CASE WHEN relation.oid IS NULL THEN NULL
         ELSE has_table_privilege(relation.oid, 'SELECT')
       END AS select_granted,
       table_info.table_name IS NOT NULL AS information_schema_visible
FROM (VALUES ('users'), ('products'), ('listings'), ('chat_rooms'),
             ('purchase_requests'), ('ownership_transfers'), ('flyway_schema_history'))
     AS expected(object_name)
LEFT JOIN pg_namespace namespace_info
  ON namespace_info.nspname = :'target_schema'
LEFT JOIN pg_class relation
  ON relation.relnamespace = namespace_info.oid
 AND relation.relname = expected.object_name
 AND relation.relkind IN ('r', 'p', 'v', 'm', 'f')
LEFT JOIN information_schema.tables table_info
  ON table_info.table_schema = :'target_schema'
 AND table_info.table_name = expected.object_name
ORDER BY expected.object_name;

SELECT relation.relname AS table_name,
       attribute.attname AS column_name,
       format_type(attribute.atttypid, attribute.atttypmod) AS data_type,
       NOT attribute.attnotnull AS is_nullable
FROM pg_attribute attribute
JOIN pg_class relation ON relation.oid = attribute.attrelid
JOIN pg_namespace namespace_info ON namespace_info.oid = relation.relnamespace
WHERE namespace_info.nspname = :'target_schema'
  AND relation.relname IN ('users', 'products', 'listings', 'chat_rooms',
                           'purchase_requests', 'ownership_transfers', 'flyway_schema_history')
  AND relation.relkind IN ('r', 'p', 'v', 'm', 'f')
  AND attribute.attnum > 0
  AND NOT attribute.attisdropped
ORDER BY relation.relname, attribute.attnum;

-- 2단계: 확인된 객체의 PK·FK·UNIQUE·CHECK와 인덱스 메타데이터.
SELECT relation.relname AS table_name,
       constraint_info.conname AS constraint_name,
       constraint_info.contype AS constraint_type,
       constraint_info.convalidated AS validated,
       pg_get_constraintdef(constraint_info.oid) AS definition
FROM pg_constraint constraint_info
JOIN pg_class relation ON relation.oid = constraint_info.conrelid
JOIN pg_namespace namespace_info ON namespace_info.oid = relation.relnamespace
WHERE namespace_info.nspname = :'target_schema'
  AND relation.relname IN ('users', 'products', 'listings', 'chat_rooms',
                           'purchase_requests', 'ownership_transfers')
ORDER BY relation.relname, constraint_info.conname;

SELECT tablename AS table_name, indexname AS index_name, indexdef AS definition
FROM pg_indexes
WHERE schemaname = :'target_schema'
  AND tablename IN ('users', 'products', 'listings', 'chat_rooms',
                    'purchase_requests', 'ownership_transfers')
ORDER BY tablename, indexname;

COMMIT;

-- 아래 절은 1단계에서 필요한 테이블·컬럼이 모두 확인된 경우에만 별도 읽기 전용
-- 트랜잭션에서 하나씩 복사해 실행한다. 누락 객체가 있으면 실행하지 않으며 0건으로 해석하지 않는다.

/* A. flyway_schema_history가 확인된 경우: 적용 이력 요약만 조회
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM <TARGET_SCHEMA>.flyway_schema_history
ORDER BY installed_rank;
COMMIT;
*/

/* B. users, products, listings가 확인된 경우: FK 후보와 판매자·현재 소유자 불일치 집계
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT
  (SELECT COUNT(*)
     FROM <TARGET_SCHEMA>.products product
     LEFT JOIN <TARGET_SCHEMA>.users owner_user ON owner_user.id = product.owner_id
    WHERE owner_user.id IS NULL) AS products_without_owner_user,
  (SELECT COUNT(*)
     FROM <TARGET_SCHEMA>.listings listing
     LEFT JOIN <TARGET_SCHEMA>.products product ON product.id = listing.product_id
    WHERE product.id IS NULL) AS listings_without_product,
  (SELECT COUNT(*)
     FROM <TARGET_SCHEMA>.listings listing
     LEFT JOIN <TARGET_SCHEMA>.users seller_user ON seller_user.id = listing.seller_id
    WHERE seller_user.id IS NULL) AS listings_without_seller_user,
  (SELECT COUNT(*)
     FROM <TARGET_SCHEMA>.listings listing
     JOIN <TARGET_SCHEMA>.products product ON product.id = listing.product_id
    WHERE listing.seller_id <> product.owner_id) AS listing_seller_owner_mismatches;
COMMIT;
*/

/* C. chat_rooms와 listings가 확인된 경우: 현재 참조와 legacy 후보를 분리한 집계
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT
  COUNT(*) AS total_rooms,
  COUNT(*) FILTER (WHERE current_listing.id IS NOT NULL) AS current_listing_matches,
  COUNT(*) FILTER (WHERE current_listing.id IS NULL) AS missing_current_listing,
  COUNT(*) FILTER (
    WHERE current_listing.id IS NOT NULL
      AND room.seller_id <> current_listing.seller_id
  ) AS current_seller_mismatches,
  COUNT(*) FILTER (
    WHERE current_listing.id IS NULL AND draft_candidate.id IS NOT NULL
  ) AS source_draft_id_candidates,
  COUNT(*) FILTER (
    WHERE current_listing.id IS NOT NULL AND draft_candidate.id IS NOT NULL
  ) AS ambiguous_numeric_matches
FROM <TARGET_SCHEMA>.chat_rooms room
LEFT JOIN <TARGET_SCHEMA>.listings current_listing ON current_listing.id = room.listing_id
LEFT JOIN <TARGET_SCHEMA>.listings draft_candidate
  ON draft_candidate.source_draft_id = room.listing_id;
COMMIT;
*/

/* D. purchase_requests까지 확인된 경우: 구매 요청 참조·방/게시글 불일치 집계
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT
  COUNT(*) AS total_purchase_requests,
  COUNT(*) FILTER (WHERE room.id IS NULL) AS missing_rooms,
  COUNT(*) FILTER (WHERE listing.id IS NULL) AS missing_listings,
  COUNT(*) FILTER (
    WHERE room.id IS NOT NULL AND listing.id IS NOT NULL
      AND room.listing_id <> request.listing_id
  ) AS room_listing_mismatches
FROM <TARGET_SCHEMA>.purchase_requests request
LEFT JOIN <TARGET_SCHEMA>.chat_rooms room ON room.id = request.room_id
LEFT JOIN <TARGET_SCHEMA>.listings listing ON listing.id = request.listing_id;
COMMIT;
*/

/* E. ownership_transfers가 확인된 경우: 정식 계약 대조용 집계
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '10s';
SELECT
  COUNT(*) AS total_transfers,
  COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_transfers,
  COUNT(*) FILTER (WHERE transfer_type = 'INTERNAL') AS internal_transfers,
  COUNT(*) FILTER (
    WHERE status = 'PENDING' AND transfer_type = 'INTERNAL'
  ) AS internal_pending_transfers
FROM <TARGET_SCHEMA>.ownership_transfers;
COMMIT;
*/
