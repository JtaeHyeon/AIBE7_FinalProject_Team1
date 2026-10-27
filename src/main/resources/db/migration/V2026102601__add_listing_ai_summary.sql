ALTER TABLE listings ADD COLUMN summary_text VARCHAR(2000);
ALTER TABLE listings ADD COLUMN summary_source_product_version BIGINT;
ALTER TABLE listings ADD COLUMN summary_source_listing_version BIGINT;
ALTER TABLE listings ADD COLUMN summary_status VARCHAR(20);
ALTER TABLE listings ADD COLUMN summary_attempt_id UUID;
ALTER TABLE listings ADD COLUMN summary_error_code VARCHAR(40);
ALTER TABLE listings ADD COLUMN summary_updated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_versions CHECK (
    (summary_source_product_version IS NULL OR summary_source_product_version >= 0)
    AND (summary_source_listing_version IS NULL OR summary_source_listing_version >= 0));
ALTER TABLE listings ADD CONSTRAINT ck_listing_summary_state CHECK (
    (summary_status IS NULL AND summary_text IS NULL AND summary_attempt_id IS NULL
        AND summary_source_product_version IS NULL AND summary_source_listing_version IS NULL
        AND summary_error_code IS NULL AND summary_updated_at IS NULL)
    OR (summary_status IS NOT NULL AND summary_status IN ('QUEUED','PROCESSING','READY','FAILED')
        AND summary_attempt_id IS NOT NULL AND summary_source_product_version IS NOT NULL
        AND summary_source_listing_version IS NOT NULL AND summary_updated_at IS NOT NULL
        AND ((summary_status IN ('QUEUED','PROCESSING') AND summary_text IS NULL AND summary_error_code IS NULL)
            OR (summary_status = 'READY' AND summary_text IS NOT NULL
                AND CHAR_LENGTH(TRIM(summary_text)) > 0 AND summary_error_code IS NULL)
            OR (summary_status = 'FAILED' AND summary_text IS NULL AND summary_error_code IS NOT NULL))));
CREATE INDEX ix_listing_summary_queue ON listings(summary_status, summary_updated_at, id);
