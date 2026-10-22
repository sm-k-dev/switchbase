CREATE TABLE keyboard_switch (
                                 id                  BIGSERIAL    PRIMARY KEY,
                                 manufacturer        VARCHAR(100) NOT NULL,
                                 model_name          VARCHAR(150) NOT NULL,
                                 switch_type         VARCHAR(20)  NOT NULL,
                                 actuation_force_g   NUMERIC(5,1),
                                 bottom_out_force_g  NUMERIC(5,1),
                                 pre_travel_mm       NUMERIC(3,1),
                                 total_travel_mm     NUMERIC(3,1),
                                 release_year        SMALLINT,
                                 created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 CONSTRAINT uk_keyboard_switch_name UNIQUE (manufacturer, model_name),
                                 CONSTRAINT ck_keyboard_switch_type CHECK (switch_type IN ('LINEAR', 'TACTILE', 'CLICKY'))
);