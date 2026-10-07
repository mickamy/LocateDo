-- +goose Up
-- One pending push per household: writes that land before the worker picks it
-- up ride along with it, and the short delay lets a burst of writes share one.
-- A message being delivered is running, not pending, so later writes queue a
-- fresh push.
-- +goose StatementBegin
CREATE FUNCTION enqueue_household_push(household uuid) RETURNS void
    LANGUAGE sql AS
$$
INSERT INTO outbox_messages (kind, payload, dedupe_key, run_at)
VALUES ('push_household', jsonb_build_object('household_id', household), 'push:' || household,
        now() + interval '5 seconds')
ON CONFLICT (dedupe_key) WHERE status = 'pending' DO NOTHING;
$$;
-- +goose StatementEnd

-- Deleting a user can cascade to their household before ON DELETE SET NULL
-- updates that household's to-dos, which then go with it, so such an update
-- passes as in record_deletion. Inserts still need the household.
-- +goose StatementBegin
CREATE FUNCTION stamp_sync_columns() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE households
    SET version = version + 1
    WHERE id = NEW.household_id
    RETURNING version INTO NEW.version;

    IF NOT FOUND THEN
        IF TG_OP = 'UPDATE' THEN
            NEW.version := OLD.version;
            RETURN NEW;
        END IF;
        RAISE foreign_key_violation USING MESSAGE = format('household %s does not exist', NEW.household_id);
    END IF;

    PERFORM enqueue_household_push(NEW.household_id);

    NEW.updated_at := now();
    RETURN NEW;
END;
$$;
-- +goose StatementEnd

-- TG_ARGV[0] names the column that identifies the row to clients.
-- When the household itself is being deleted, nobody is left to sync, so no tombstone is written.
-- +goose StatementBegin
CREATE FUNCTION record_deletion() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    next_version bigint;
BEGIN
    UPDATE households
    SET version = version + 1
    WHERE id = OLD.household_id
    RETURNING version INTO next_version;

    IF FOUND THEN
        INSERT INTO deletions (household_id, table_name, row_id, version)
        VALUES (OLD.household_id, TG_TABLE_NAME, (to_jsonb(OLD) ->> TG_ARGV[0])::uuid, next_version);
        PERFORM enqueue_household_push(OLD.household_id);
    END IF;

    RETURN NULL;
END;
$$;
-- +goose StatementEnd

CREATE TRIGGER memberships_stamp_sync_columns
    BEFORE INSERT OR UPDATE
    ON memberships
    FOR EACH ROW
EXECUTE FUNCTION stamp_sync_columns();

CREATE TRIGGER memberships_record_deletion
    AFTER DELETE
    ON memberships
    FOR EACH ROW
EXECUTE FUNCTION record_deletion('user_id');

CREATE TRIGGER categories_stamp_sync_columns
    BEFORE INSERT OR UPDATE
    ON categories
    FOR EACH ROW
EXECUTE FUNCTION stamp_sync_columns();

CREATE TRIGGER categories_record_deletion
    AFTER DELETE
    ON categories
    FOR EACH ROW
EXECUTE FUNCTION record_deletion('id');

CREATE TRIGGER places_stamp_sync_columns
    BEFORE INSERT OR UPDATE
    ON places
    FOR EACH ROW
EXECUTE FUNCTION stamp_sync_columns();

CREATE TRIGGER places_record_deletion
    AFTER DELETE
    ON places
    FOR EACH ROW
EXECUTE FUNCTION record_deletion('id');

CREATE TRIGGER todos_stamp_sync_columns
    BEFORE INSERT OR UPDATE
    ON todos
    FOR EACH ROW
EXECUTE FUNCTION stamp_sync_columns();

CREATE TRIGGER todos_record_deletion
    AFTER DELETE
    ON todos
    FOR EACH ROW
EXECUTE FUNCTION record_deletion('id');

-- +goose Down
DROP TRIGGER todos_record_deletion ON todos;
DROP TRIGGER todos_stamp_sync_columns ON todos;
DROP TRIGGER places_record_deletion ON places;
DROP TRIGGER places_stamp_sync_columns ON places;
DROP TRIGGER categories_record_deletion ON categories;
DROP TRIGGER categories_stamp_sync_columns ON categories;
DROP TRIGGER memberships_record_deletion ON memberships;
DROP TRIGGER memberships_stamp_sync_columns ON memberships;
DROP FUNCTION record_deletion();
DROP FUNCTION stamp_sync_columns();
DROP FUNCTION enqueue_household_push(uuid);
