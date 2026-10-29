-- 기존 긴 요약은 보존하되 카드 형식으로 검증·재생성되기 전에는 목록에 제공하지 않는다.
ALTER TABLE listings ADD COLUMN summary_format_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_format_version CHECK (summary_format_version >= 0);
ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_card_length CHECK (
    summary_format_version = 0 OR summary_text IS NULL OR CHAR_LENGTH(summary_text) <= 90);
