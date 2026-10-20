-- Local demo/test fixtures only. This location is excluded from normal Flyway runs.
CREATE TABLE products (
    id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'LAPTOP',
    title VARCHAR(200) NOT NULL, manufacturer VARCHAR(100) NOT NULL,
    model_name VARCHAR(100) NOT NULL, purchase_date DATE, warranty_expires_at DATE,
    serial_number_fingerprint VARCHAR(64) NOT NULL,
    serial_number_ciphertext VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_products_serial_number_fingerprint UNIQUE (serial_number_fingerprint)
);
CREATE TABLE laptop_details (
    product_id BIGINT PRIMARY KEY REFERENCES products(id),
    cpu_spec VARCHAR(100), gpu_spec VARCHAR(100), ram_gb INTEGER,
    storage_gb INTEGER, storage_type VARCHAR(20), display_spec VARCHAR(100),
    battery_health_percent INTEGER, os_name VARCHAR(50)
);
CREATE TABLE product_images (
    id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id),
    storage_key VARCHAR(500) NOT NULL, content_type VARCHAR(100) NOT NULL DEFAULT 'image/png',
    sort_order INTEGER NOT NULL DEFAULT 0, deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE product_events (
    id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE'
);
CREATE TABLE listings (id BIGINT PRIMARY KEY, product_id BIGINT, status VARCHAR(20));
INSERT INTO products (id, owner_id, title, manufacturer, model_name, purchase_date,
    warranty_expires_at, serial_number_fingerprint, serial_number_ciphertext) VALUES
    (101, 1, 'MacBook Pro 14 M3 Pro', 'Apple', 'MacBook Pro 14', DATE '2024-02-12', DATE '2027-02-12', 'demo-101', 'demo-cipher-101'),
    (102, 1, 'ThinkPad X1 Carbon', 'Lenovo', 'ThinkPad X1 Carbon Gen 12', DATE '2025-03-15', DATE '2028-03-15', 'demo-102', 'demo-cipher-102'),
    (201, 2, '다른 소유자의 노트북', 'Samsung', 'Galaxy Book', DATE '2025-01-01', NULL, 'demo-201', 'demo-cipher-201');
INSERT INTO products (id, owner_id, title, manufacturer, model_name, archived_at,
    serial_number_fingerprint, serial_number_ciphertext)
    VALUES (103, 1, '보관된 제품', 'LG', 'Gram', CURRENT_TIMESTAMP, 'demo-103', 'demo-cipher-103');
INSERT INTO laptop_details (product_id, cpu_spec, ram_gb, storage_gb, storage_type, display_spec, battery_health_percent, os_name) VALUES
    (101, 'Apple M3 Pro', 18, 512, 'SSD', '14인치', 92, 'macOS'),
    (102, 'Intel Core Ultra 7', 32, 1024, 'SSD', '14인치', NULL, 'Windows 11');
INSERT INTO product_images (id, product_id, storage_key, sort_order) VALUES
    (301, 101, 'draft-demo-front.png', 0), (302, 101, 'draft-demo-side.png', 1),
    (303, 101, 'draft-demo-back.png', 2), (304, 102, 'draft-demo-thinkpad.png', 0),
    (401, 201, 'draft-demo-other.png', 0);
INSERT INTO product_images (id, product_id, storage_key, sort_order, deleted_at)
    VALUES (399, 101, 'draft-demo-deleted.png', 3, CURRENT_TIMESTAMP);
INSERT INTO product_events (id, product_id) VALUES (1, 101), (2, 102);
