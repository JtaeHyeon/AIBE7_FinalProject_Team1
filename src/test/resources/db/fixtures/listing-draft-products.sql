-- 실제 운영 마이그레이션으로 생성한 제품 테이블에 테스트 데이터만 넣는다.
INSERT INTO products (id, owner_id, category, title, manufacturer, manufacturer_normalized, model_name,
                      purchase_date, warranty_expires_at, serial_number_fingerprint, serial_number_ciphertext)
VALUES (101, 1, 'LAPTOP', 'MacBook Pro 14 M3 Pro', 'Apple', 'APPLE', 'MacBook Pro 14',
        DATE '2024-02-12', DATE '2027-02-12', 'fixture-101', 'test-ciphertext-101'),
       (102, 1, 'LAPTOP', 'ThinkPad X1 Carbon', 'Lenovo', 'LENOVO', 'ThinkPad X1 Carbon Gen 12',
        DATE '2025-03-15', DATE '2028-03-15', 'fixture-102', 'test-ciphertext-102'),
       (201, 2, 'LAPTOP', '다른 소유자의 노트북', 'Samsung', 'SAMSUNG', 'Galaxy Book',
        DATE '2025-01-01', NULL, 'fixture-201', 'test-ciphertext-201');
INSERT INTO products (id, owner_id, category, title, manufacturer, manufacturer_normalized, model_name,
                      serial_number_fingerprint, serial_number_ciphertext, archived_at)
VALUES (103, 1, 'LAPTOP', '보관된 제품', 'LG', 'LG', 'Gram', 'fixture-103', 'test-ciphertext-103', CURRENT_TIMESTAMP);

INSERT INTO laptop_details (product_id, cpu_spec, ram_gb, storage_gb, storage_type,
                            display_spec, battery_health_percent, os_name)
VALUES (101, 'Apple M3 Pro', 18, 512, 'SSD', '14인치', 92, 'macOS'),
       (102, 'Intel Core Ultra 7', 32, 1024, 'SSD', '14인치', NULL, 'Windows 11');

INSERT INTO product_images (id, product_id, storage_key, content_type, sort_order)
VALUES (301, 101, 'draft-demo-front.png', 'image/png', 0),
       (302, 101, 'draft-demo-side.png', 'image/png', 1),
       (303, 101, 'draft-demo-back.png', 'image/png', 2),
       (304, 102, 'draft-demo-thinkpad.png', 'image/png', 0),
       (401, 201, 'draft-demo-other.png', 'image/png', 0);
INSERT INTO product_images (id, product_id, storage_key, content_type, sort_order, deleted_at)
VALUES (399, 101, 'draft-demo-deleted.png', 'image/png', 3, CURRENT_TIMESTAMP);
