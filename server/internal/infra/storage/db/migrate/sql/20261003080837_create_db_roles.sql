-- +goose Up
-- +goose StatementBegin
DO
$$
BEGIN
        -- Role creation here serves local development and CI. In environments where
        -- the migration runner lacks CREATEROLE (managed databases), pre-provision
        -- these roles via infrastructure tooling; creation is then skipped and only
        -- the grants below apply. Passwords are local/CI values — production
        -- rotates them out-of-band via ALTER ROLE.
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'locatedo_writer') THEN
            CREATE ROLE locatedo_writer LOGIN PASSWORD 'password';
        END IF;
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'locatedo_reader') THEN
            CREATE ROLE locatedo_reader LOGIN PASSWORD 'password';
        END IF;
END
$$;
-- +goose StatementEnd

-- +goose StatementBegin
DO
$$
BEGIN
        EXECUTE 'GRANT CONNECT ON DATABASE ' || quote_ident(current_database()) || ' TO locatedo_writer, locatedo_reader';
END
$$;
-- +goose StatementEnd

GRANT USAGE ON SCHEMA public TO locatedo_writer, locatedo_reader;

-- Tables and sequences created by the role running migrations pick these up automatically.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO locatedo_writer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO locatedo_writer;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO locatedo_reader;

-- +goose Down
-- DROP OWNED revokes every privilege granted to these roles in the current
-- database and on shared objects (CONNECT), including per-object grants and
-- default-privilege entries that schema-level REVOKE would miss. The roles
-- own no objects here. Existence checks keep the down runnable when roles
-- were removed out-of-band.
-- +goose StatementBegin
DO
$$
BEGIN
        IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'locatedo_writer') THEN
            DROP OWNED BY locatedo_writer;
        END IF;
        IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'locatedo_reader') THEN
            DROP OWNED BY locatedo_reader;
        END IF;
END
$$;
-- +goose StatementEnd
DROP ROLE IF EXISTS locatedo_writer;
DROP ROLE IF EXISTS locatedo_reader;
