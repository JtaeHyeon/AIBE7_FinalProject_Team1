-- 노트북 전용이던 제품 분류 제약을 가전 분류까지 허용한다. 기존 노트북 제품은 그대로 유효하다.
ALTER TABLE products DROP CONSTRAINT IF EXISTS ck_products_category;
ALTER TABLE products ADD CONSTRAINT ck_products_category CHECK (category IN (
    'LAPTOP', 'MOBILE', 'DISPLAY', 'LARGE_APPLIANCE', 'CLEANING_AIR', 'CLIMATE', 'KITCHEN', 'AUDIO_GAME'));
