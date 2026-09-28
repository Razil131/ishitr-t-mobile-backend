ALTER TABLE user_limits 
    DROP CONSTRAINT IF EXISTS fk_user_limit_group_id,
    DROP CONSTRAINT IF EXISTS uq_user_limit_per_resource;

DROP INDEX IF EXISTS idx_user_limits_group_id;

ALTER TABLE user_limits 
    DROP COLUMN IF EXISTS group_id;

ALTER TABLE user_limits 
    ADD CONSTRAINT uq_user_limit_per_resource UNIQUE (user_id, resource_type);