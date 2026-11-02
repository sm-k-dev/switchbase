-- 콜라보: manufacturer = 실제로 만든 회사, brand = 기획하거나 판매한 쪽 (혼자 낸 스위치는 비워 둔다)
ALTER TABLE keyboard_switch ADD COLUMN brand VARCHAR(100);
ALTER TABLE switch_revision ADD COLUMN brand VARCHAR(100);

-- 이름은 같은데 무게가 다른 버전 (Y3 45g, Y3 55g). 하나뿐이면 비워 둔다.
ALTER TABLE keyboard_switch ADD COLUMN variant VARCHAR(50);
ALTER TABLE switch_revision ADD COLUMN variant VARCHAR(50);

-- 같은 스위치 검사를 제조사 + 이름 + 변형으로 바꾼다. 변형이 비어 있는 것끼리도 같은 걸로 본다.
ALTER TABLE keyboard_switch DROP CONSTRAINT uk_keyboard_switch_name;
ALTER TABLE keyboard_switch ADD CONSTRAINT uk_keyboard_switch_name
    UNIQUE NULLS NOT DISTINCT (manufacturer, model_name, variant);

-- 스위치 하나의 다른 이름들 (저월백, 저소음 월백 ...)
CREATE TABLE switch_alias (
                              id          BIGSERIAL    PRIMARY KEY,
                              switch_id   BIGINT       NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
                              alias       VARCHAR(150) NOT NULL,
                              normalized  VARCHAR(150) NOT NULL,
                              CONSTRAINT uk_switch_alias UNIQUE (switch_id, normalized)
);

CREATE INDEX ix_switch_alias_normalized ON switch_alias (normalized);

-- 제조사의 다른 표기 → 대표 이름 (게이트론 → Gateron)
CREATE TABLE manufacturer_alias (
                                    id            BIGSERIAL    PRIMARY KEY,
                                    manufacturer  VARCHAR(100) NOT NULL,
                                    alias         VARCHAR(100) NOT NULL,
                                    normalized    VARCHAR(100) NOT NULL,
                                    CONSTRAINT uk_manufacturer_alias UNIQUE (normalized)
);

INSERT INTO manufacturer_alias (manufacturer, alias, normalized) VALUES
                                                                     ('Gateron', '게이트론', '게이트론'),
                                                                     ('Cherry',  '체리',     '체리'),
                                                                     ('Kailh',   '카일',     '카일');