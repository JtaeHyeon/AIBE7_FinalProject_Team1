-- P3 거래 변경 확인용 pending DDL.
-- product_events와 정식 ownership DDL의 제공·순서가 확정되기 전에는 일반 Flyway 경로에 편입하지 않는다.

ALTER TABLE purchase_requests
    ADD CONSTRAINT ck_purchase_requests_cancellation_metadata
    CHECK (
        (
            request_status = 'CANCELLED'
            AND transaction_status = 'CANCELLED'
            AND cancelled_by_id IS NOT NULL
            AND cancel_reason IN (
                'SELF_CIRCUMSTANCES',
                'MUTUAL_AGREEMENT',
                'COUNTERPART_UNREACHABLE',
                'CONDITION_MISMATCH'
            )
            AND cancelled_at IS NOT NULL
        )
        OR
        (
            (request_status <> 'CANCELLED' OR transaction_status IS DISTINCT FROM 'CANCELLED')
            AND cancelled_by_id IS NULL
            AND cancel_reason IS NULL
            AND cancelled_at IS NULL
        )
    );

CREATE TABLE transaction_change_reviews (
    purchase_request_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    facts_version BIGINT NOT NULL,
    change_facts JSONB NOT NULL,
    decision VARCHAR(20),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (purchase_request_id, event_id),
    CONSTRAINT fk_transaction_change_reviews_request
        FOREIGN KEY (purchase_request_id) REFERENCES purchase_requests (id),
    CONSTRAINT fk_transaction_change_reviews_event
        FOREIGN KEY (event_id) REFERENCES product_events (id),
    CONSTRAINT ck_transaction_change_reviews_facts_version CHECK (facts_version > 0),
    CONSTRAINT ck_transaction_change_reviews_facts_object
        CHECK (jsonb_typeof(change_facts) = 'object'),
    CONSTRAINT ck_transaction_change_reviews_decision
        CHECK (decision IS NULL OR decision IN ('KEEP', 'CANCEL')),
    CONSTRAINT ck_transaction_change_reviews_reviewed_pair
        CHECK (
            (decision IS NULL AND reviewed_at IS NULL)
            OR (decision IS NOT NULL AND reviewed_at IS NOT NULL)
        )
);

CREATE INDEX ix_transaction_change_reviews_unreviewed
    ON transaction_change_reviews (purchase_request_id, event_id)
    WHERE decision IS NULL;
