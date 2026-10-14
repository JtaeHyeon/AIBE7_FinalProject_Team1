ALTER TABLE products
    ADD COLUMN serial_last4 VARCHAR(4);

UPDATE products
SET serial_last4 = '****'
WHERE serial_last4 IS NULL;

ALTER TABLE products
    ALTER COLUMN serial_last4 SET NOT NULL,
    DROP CONSTRAINT ck_products_serial_ciphertext_required,
    DROP COLUMN serial_number_ciphertext;

ALTER TABLE products
    ADD CONSTRAINT ck_products_serial_last4
        CHECK (char_length(serial_last4) BETWEEN 1 AND 4);

CREATE INDEX ix_products_owner_created
    ON products (owner_id, created_at, id);
