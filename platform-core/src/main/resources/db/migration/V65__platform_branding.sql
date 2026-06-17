CREATE TABLE platform_branding (
    id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    logo_path       VARCHAR(512),
    logo_mime       VARCHAR(100),
    updated_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform_branding (id) VALUES (1) ON CONFLICT DO NOTHING;
