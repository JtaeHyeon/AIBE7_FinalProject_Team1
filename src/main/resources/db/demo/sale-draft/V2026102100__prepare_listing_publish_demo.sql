-- 과거 데모의 빈 판매글 모형은 보존하고 운영 마이그레이션이 실제 테이블을 생성하게 한다.
ALTER TABLE listings RENAME TO legacy_demo_listings;
INSERT INTO users (id, email, password_hash, nickname, role, created_at, updated_at)
SELECT 1, 'owner1@listing-demo.invalid', 'demo-not-authenticatable', '데모소유자1', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 1);
INSERT INTO users (id, email, password_hash, nickname, role, created_at, updated_at)
SELECT 2, 'owner2@listing-demo.invalid', 'demo-not-authenticatable', '데모소유자2', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2);
