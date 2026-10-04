-- +goose Up
CREATE TABLE users
(
    id           uuid PRIMARY KEY     DEFAULT uuidv7(),
    display_name text        NOT NULL DEFAULT '',
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_identities
(
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   text        NOT NULL CHECK (provider IN ('apple', 'google')),
    subject    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, provider),
    UNIQUE (provider, subject)
);

CREATE TABLE refresh_tokens
(
    id         uuid PRIMARY KEY     DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  uuid        NOT NULL,
    token_hash bytea       NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);

CREATE TABLE apple_tokens
(
    user_id                  uuid PRIMARY KEY     REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_ciphertext bytea       NOT NULL,
    updated_at               timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE devices
(
    id           uuid PRIMARY KEY     DEFAULT uuidv7(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform     text        NOT NULL CHECK (platform IN ('ios', 'android')),
    push_token   text        NOT NULL,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (platform, push_token)
);

CREATE INDEX devices_user_id_idx ON devices (user_id);

CREATE TABLE households
(
    id            uuid PRIMARY KEY     DEFAULT uuidv7(),
    owner_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan          text        NOT NULL DEFAULT 'free' CHECK (plan IN ('free', 'pro')),
    version       bigint      NOT NULL DEFAULT 0,
    swept_version bigint      NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX households_owner_id_idx ON households (owner_id);

CREATE TABLE memberships
(
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         text        NOT NULL CHECK (role IN ('owner', 'member')),
    joined_at    timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL,
    PRIMARY KEY (household_id, user_id),
    UNIQUE (user_id)
);

CREATE INDEX memberships_household_id_version_idx ON memberships (household_id, version);

CREATE TABLE household_invites
(
    id           uuid PRIMARY KEY     DEFAULT uuidv7(),
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    token_hash   bytea       NOT NULL UNIQUE,
    created_by   uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at   timestamptz NOT NULL,
    accepted_by  uuid REFERENCES users (id) ON DELETE SET NULL,
    accepted_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX household_invites_household_id_idx ON household_invites (household_id);

CREATE TABLE categories
(
    id           uuid PRIMARY KEY     DEFAULT uuidv7(),
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    builtin_key  text CHECK (builtin_key IN ('shopping', 'work', 'life', 'other')),
    name         text,
    icon         text        NOT NULL,
    color        text        NOT NULL,
    sort_order   integer     NOT NULL DEFAULT 0,
    updated_at   timestamptz NOT NULL,
    version      bigint      NOT NULL,
    UNIQUE (id, household_id),
    UNIQUE (household_id, builtin_key),
    CHECK (builtin_key IS NOT NULL OR name IS NOT NULL)
);

CREATE INDEX categories_household_id_version_idx ON categories (household_id, version);

CREATE TABLE places
(
    id           uuid PRIMARY KEY          DEFAULT uuidv7(),
    household_id uuid             NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name         text             NOT NULL,
    lat          double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng          double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
    radius_m     integer          NOT NULL DEFAULT 100 CHECK (radius_m BETWEEN 50 AND 500),
    category_id  uuid,
    sort_order   integer          NOT NULL DEFAULT 0,
    updated_at   timestamptz      NOT NULL,
    version      bigint           NOT NULL,
    UNIQUE (id, household_id),
    FOREIGN KEY (category_id, household_id)
        REFERENCES categories (id, household_id) ON DELETE SET NULL (category_id)
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX places_household_id_version_idx ON places (household_id, version);
CREATE INDEX places_category_id_idx ON places (category_id);

CREATE TABLE todos
(
    id               uuid PRIMARY KEY     DEFAULT uuidv7(),
    household_id     uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    place_id         uuid        NOT NULL,
    title            text        NOT NULL,
    assignee_id      uuid REFERENCES users (id) ON DELETE SET NULL,
    completed_at     timestamptz,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL,
    UNIQUE (id, household_id),
    FOREIGN KEY (place_id, household_id)
        REFERENCES places (id, household_id) ON DELETE CASCADE
        DEFERRABLE INITIALLY IMMEDIATE
);

CREATE INDEX todos_household_id_version_idx ON todos (household_id, version);
CREATE INDEX todos_place_id_idx ON todos (place_id);

CREATE TABLE deletions
(
    id           uuid PRIMARY KEY     DEFAULT uuidv7(),
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    table_name   text        NOT NULL CHECK (table_name IN ('memberships', 'categories', 'places', 'todos')),
    row_id       uuid        NOT NULL,
    version      bigint      NOT NULL,
    deleted_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX deletions_household_id_version_idx ON deletions (household_id, version);

CREATE TABLE outbox_messages
(
    id         uuid PRIMARY KEY     DEFAULT uuidv7(),
    kind       text        NOT NULL,
    payload    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key text,
    status     text        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'dead')),
    run_at     timestamptz NOT NULL DEFAULT now(),
    attempts   integer     NOT NULL DEFAULT 0,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX outbox_messages_pending_dedupe_key_idx ON outbox_messages (dedupe_key) WHERE status = 'pending';
CREATE INDEX outbox_messages_pending_run_at_idx ON outbox_messages (run_at) WHERE status = 'pending';
CREATE INDEX deletions_deleted_at_idx ON deletions (deleted_at);

-- +goose Down
DROP TABLE outbox_messages;
DROP TABLE deletions;
DROP TABLE household_invites;
DROP TABLE todos;
DROP TABLE places;
DROP TABLE categories;
DROP TABLE memberships;
DROP TABLE households;
DROP TABLE devices;
DROP TABLE apple_tokens;
DROP TABLE refresh_tokens;
DROP TABLE user_identities;
DROP TABLE users;
