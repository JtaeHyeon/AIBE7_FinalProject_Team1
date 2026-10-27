-- 로컬 시연 전용. 기존 DB에서 누락된 가상 소유자만 복구하고 기존 계정은 수정하지 않는다.
-- 일반 실행의 Flyway 경로에는 이 디렉터리가 포함되지 않는다.
INSERT INTO users (id, email, password_hash, nickname, role, created_at, updated_at)
SELECT 1, 'owner1@listing-demo.invalid', 'demo-not-authenticatable', '데모소유자1', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 1);
INSERT INTO users (id, email, password_hash, nickname, role, created_at, updated_at)
SELECT 2, 'owner2@listing-demo.invalid', 'demo-not-authenticatable', '데모소유자2', 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2);
