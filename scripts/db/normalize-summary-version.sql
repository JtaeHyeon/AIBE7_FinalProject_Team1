-- 일회성 PostgreSQL 복구용이다. Flyway 자동 실행 경로에 넣지 않는다.
-- 서버를 멈추고 DB를 백업한 뒤 normalize-summary-version.psql로 실행한다.
DO $alignment$
DECLARE
    target_schema TEXT := current_schema();
    applied RECORD;
    canonical_count INTEGER;
    renamed_count INTEGER;
    changed_count INTEGER;
BEGIN
    IF current_setting('pastport.expected_database', TRUE) IS DISTINCT FROM current_database()
       OR current_setting('pastport.expected_schema', TRUE) IS DISTINCT FROM target_schema THEN
        RAISE EXCEPTION 'Expected database/schema must match the current connection';
    END IF;
    IF target_schema IS NULL OR target_schema = 'information_schema' OR target_schema LIKE 'pg\_%' ESCAPE '\'
       OR to_regclass(format('%I.flyway_schema_history', target_schema)) IS NULL THEN
        RAISE EXCEPTION 'The selected application schema has no Flyway history';
    END IF;

    PERFORM set_config('lock_timeout', '5s', TRUE);
    EXECUTE format('LOCK TABLE %I.flyway_schema_history IN ACCESS EXCLUSIVE MODE', target_schema);

    -- 소유권 구버전도 사용한 DB는 별도 조정이 필요하므로 자동 처리하지 않는다.
    IF EXISTS (
        SELECT 1 FROM flyway_schema_history
        WHERE (version = '2026102702' AND script = 'V2026102702__create_ownership_tables.sql')
           OR (version = '2026102707' AND script = 'V2026102707__add_ownership_pending_slot.sql')
    ) THEN
        RAISE EXCEPTION 'An old ownership migration is already recorded; manual review required';
    END IF;

    SELECT count(*) INTO canonical_count FROM flyway_schema_history WHERE version = '2026102701';
    SELECT count(*) INTO renamed_count FROM flyway_schema_history WHERE version = '2026102706';
    IF canonical_count + renamed_count <> 1 THEN
        RAISE EXCEPTION 'Exactly one of summary versions 2026102701/2026102706 must exist';
    END IF;
    SELECT * INTO applied FROM flyway_schema_history
    WHERE version IN ('2026102701', '2026102706');
    IF applied.checksum IS DISTINCT FROM 620449880
       OR applied.success IS DISTINCT FROM TRUE
       OR applied.type IS DISTINCT FROM 'SQL'
       OR applied.description IS DISTINCT FROM 'support listing summary refresh'
       OR applied.script IS DISTINCT FROM ('V' || applied.version || '__support_listing_summary_refresh.sql') THEN
        RAISE EXCEPTION 'Summary migration identity/checksum/status does not match the verified SQL';
    END IF;

    -- 기록과 달리 실제 DDL이 끝나지 않은 DB는 이력만 맞추지 않고 중단한다.
    IF (SELECT count(*) FROM information_schema.columns
        WHERE table_schema = target_schema AND table_name = 'listings'
          AND column_name IN ('summary_requested_at', 'summary_available_at')
          AND data_type = 'timestamp with time zone') <> 2
       OR NOT EXISTS (
           SELECT 1 FROM pg_constraint
           WHERE conrelid = to_regclass(format('%I.listings', target_schema))
             AND conname = 'ck_listing_summary_state'
             AND pg_get_constraintdef(oid) LIKE '%summary_requested_at%'
             AND pg_get_constraintdef(oid) LIKE '%summary_available_at%'
       ) THEN
        RAISE EXCEPTION 'Expected summary columns/constraint are missing; no history change performed';
    END IF;

    IF canonical_count = 1 THEN
        RAISE NOTICE 'Already canonical: 2026102701; no change';
        RETURN;
    END IF;

    UPDATE flyway_schema_history
    SET version = '2026102701', script = 'V2026102701__support_listing_summary_refresh.sql'
    WHERE installed_rank = applied.installed_rank AND version = '2026102706';
    GET DIAGNOSTICS changed_count = ROW_COUNT;
    IF changed_count <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one history row to change';
    END IF;
    RAISE NOTICE 'Aligned 2026102706 to 2026102701; business data and checksum unchanged';
END;
$alignment$;
