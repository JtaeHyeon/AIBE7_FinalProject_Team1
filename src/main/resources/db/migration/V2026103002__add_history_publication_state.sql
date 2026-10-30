-- 누적 핵심 이력의 판매글 공개 상태와 개인정보 검토 결과를 저장한다.
-- 기존 이력은 검토되지 않은 것으로 시작해 공개 화면에서 자유 입력을 노출하지 않는다.

ALTER TABLE product_events
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE product_events
    ADD COLUMN IF NOT EXISTS public_text_reviewed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product_events ADD CONSTRAINT ck_product_events_visibility
    CHECK (visibility IN ('PRIVATE', 'PUBLIC'));

ALTER TABLE audit_logs
    ADD COLUMN public_reason_reviewed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_product_events_publication
    ON product_events (product_id, visibility, occurred_on, created_at, id);
