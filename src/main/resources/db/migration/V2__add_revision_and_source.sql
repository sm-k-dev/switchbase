ALTER TABLE keyboard_switch
    ADD COLUMN description TEXT,
    ADD COLUMN version     BIGINT NOT NULL DEFAULT 0;

CREATE INDEX ix_keyboard_switch_type ON keyboard_switch (switch_type);

CREATE TABLE switch_revision (
                                 id                  BIGSERIAL    PRIMARY KEY,
                                 switch_id           BIGINT       NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
                                 revision_no         INT          NOT NULL,
                                 manufacturer        VARCHAR(100) NOT NULL,
                                 model_name          VARCHAR(150) NOT NULL,
                                 switch_type         VARCHAR(20)  NOT NULL,
                                 actuation_force_g   NUMERIC(5,1),
                                 bottom_out_force_g  NUMERIC(5,1),
                                 pre_travel_mm       NUMERIC(3,1),
                                 total_travel_mm     NUMERIC(3,1),
                                 release_year        SMALLINT,
                                 description         TEXT,
                                 edit_summary        VARCHAR(200) NOT NULL,
                                 editor              VARCHAR(50)  NOT NULL,
                                 created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 CONSTRAINT uk_switch_revision_no UNIQUE (switch_id, revision_no)
);

CREATE TABLE spec_source (
                             id          BIGSERIAL    PRIMARY KEY,
                             switch_id   BIGINT       NOT NULL REFERENCES keyboard_switch (id) ON DELETE CASCADE,
                             source_type VARCHAR(20)  NOT NULL,
                             url         VARCHAR(500) NOT NULL,
                             note        VARCHAR(200),
                             created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                             CONSTRAINT ck_spec_source_type CHECK (source_type IN ('OFFICIAL', 'RETAILER', 'REVIEW', 'COMMUNITY'))
);

CREATE INDEX ix_spec_source_switch ON spec_source (switch_id);