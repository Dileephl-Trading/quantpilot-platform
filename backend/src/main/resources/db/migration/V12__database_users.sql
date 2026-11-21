CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    roles VARCHAR(255) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_username CHECK (length(trim(username)) >= 3),
    CONSTRAINT ck_app_user_password_hash CHECK (password_hash IS NULL OR length(trim(password_hash)) >= 50)
);

CREATE INDEX ix_app_user_enabled ON app_user (enabled);

INSERT INTO app_user (username, password_hash, roles, enabled)
VALUES ('quantpilot', NULL, 'USER', FALSE);