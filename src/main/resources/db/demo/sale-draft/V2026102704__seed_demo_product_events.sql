-- 데모 DB 전용: 정식 product_events(V2026102703) 생성 뒤에 데모 이력 두 건을 다시 넣는다.
INSERT INTO product_events (product_id, author_id, event_type, occurred_on, title, visibility)
VALUES (101, 1, 'PURCHASE', DATE '2024-02-12', '구매', 'PRIVATE');
INSERT INTO product_events (product_id, author_id, event_type, occurred_on, title, visibility)
VALUES (102, 1, 'PURCHASE', DATE '2025-03-15', '구매', 'PRIVATE');
