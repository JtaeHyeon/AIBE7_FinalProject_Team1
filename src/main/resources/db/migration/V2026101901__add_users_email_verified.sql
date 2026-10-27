-- 가입 후 이메일 인증. 이미 가입한 회원은 인증을 마친 것으로 보고, 새로 가입하는 회원은 미인증으로 시작한다.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;
