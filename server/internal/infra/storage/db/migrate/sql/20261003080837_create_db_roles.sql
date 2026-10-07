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
-- Roles are shared by every database in the cluster, so this only takes back
-- what the up granted in this database (DROP OWNED would also strip CONNECT on
-- other databases) and drops a role only when nothing else still depends on it.
-- Existence checks keep the down runnable when roles were removed out-of-band.
-- +goose StatementBegin
DO
$$
DECLARE
    role_name text;
BEGIN
    FOREACH role_name IN ARRAY ARRAY ['locatedo_writer', 'locatedo_reader']
        LOOP
            IF EXISTS (SELECT FROM pg_roles WHERE rolname = role_name) THEN
                EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', role_name);
                EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', role_name);
                EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', role_name);
                EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', role_name);
                EXECUTE format('REVOKE USAGE ON SCHEMA public FROM %I', role_name);
                EXECUTE format('REVOKE CONNECT ON DATABASE %I FROM %I', current_database(), role_name);
                IF NOT EXISTS (SELECT
                               FROM pg_shdepend
                               WHERE refclassid = 'pg_authid'::regclass
                                 AND refobjid = (SELECT oid FROM pg_roles WHERE rolname = role_name)) THEN
                    EXECUTE format('DROP ROLE %I', role_name);
                END IF;
            END IF;
        END LOOP;
END
$$;
-- +goose StatementEnd
