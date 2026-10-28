-- 정식 이력 V2026102801이 적용된 DB에도 카드 요약을 순서대로 추가한다.
-- 시연 DB의 기존 V2026102702 컬럼·값을 유지하며 같은 제약으로 정렬한다.
ALTER TABLE listings ADD COLUMN IF NOT EXISTS summary_format_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE listings DROP CONSTRAINT IF EXISTS ck_listing_summary_format_version;
ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_format_version CHECK (summary_format_version >= 0);
ALTER TABLE listings DROP CONSTRAINT IF EXISTS ck_listing_summary_card_length;
ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_card_length CHECK (
    summary_format_version = 0 OR summary_text IS NULL OR CHAR_LENGTH(summary_text) <= 90);
