-- ERD의 초안 사진 참조 구조로 옮긴다. 기존 초안 내용·사진 순서·대표 선택은 보존한다.
-- 제품 스키마와 현재 사양 계산은 PROD가 관리하고 SALE은 제품 서비스를 통해 조회한다.
ALTER TABLE listing_drafts ADD CONSTRAINT fk_listing_drafts_product
    FOREIGN KEY (product_id) REFERENCES products(id);

CREATE TABLE draft_images (
    draft_id BIGINT NOT NULL,
    product_image_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 19),
    is_primary BOOLEAN NOT NULL,
    PRIMARY KEY (draft_id, product_image_id),
    CONSTRAINT fk_draft_images_draft FOREIGN KEY (draft_id)
        REFERENCES listing_drafts(id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_images_product_image FOREIGN KEY (product_image_id)
        REFERENCES product_images(id),
    CONSTRAINT uk_draft_images_order UNIQUE (draft_id, sort_order),
    -- 기존 API의 첫 번째 선택 사진 = 대표 사진 규칙을 유지한다.
    CONSTRAINT ck_draft_images_primary_order CHECK (is_primary = (sort_order = 0))
);

INSERT INTO draft_images (draft_id, product_image_id, sort_order, is_primary)
SELECT draft_id, image_id, sort_order, sort_order = 0 FROM listing_draft_images;

DROP TABLE listing_draft_images;
