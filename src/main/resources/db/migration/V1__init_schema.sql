-- ENUMS
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_group_status') THEN
        CREATE TYPE E_GROUP_STATUS AS ENUM ('ACTIVE', 'DISBANDED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_role') THEN
        CREATE TYPE E_ROLE AS ENUM ('OWNER', 'MEMBER');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_member_status') THEN
        CREATE TYPE E_MEMBER_STATUS AS ENUM ('ACTIVE', 'LEAVE', 'EXCLUDED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_resource_type') THEN
        CREATE TYPE E_RESOURCE_TYPE AS ENUM ('TRAFFIC', 'MINUTES');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_scope_type') THEN
        CREATE TYPE E_SCOPE_TYPE AS ENUM ('USER', 'GROUP');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'e_action_type') THEN
        CREATE TYPE E_ACTION_TYPE AS ENUM ('ADD_MEMBER', 'REMOVE_MEMBER', 'CHANGE_LIMIT', 'TRANSFER_OWNERSHIP', 'DISBAND_GROUP');
    END IF;
END $$;

-- Домен #1
--  #1
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

--  #2
CREATE TABLE IF NOT EXISTS auth_codes(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code  VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_auth_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

--  #3
CREATE TABLE IF NOT EXISTS user_sessions(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    access_token VARCHAR(255) UNIQUE NOT NULL,
    refresh_token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user_sessions FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Домен #2
--  #4
CREATE TABLE IF NOT EXISTS family_groups(
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    status E_GROUP_STATUS NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_family_groups FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT
);

--  #5
CREATE TABLE IF NOT EXISTS group_members(
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL ,
    user_id BIGINT NOT NULL,
    role E_ROLE NOT NULL DEFAULT 'MEMBER',
    status E_MEMBER_STATUS NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_members_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_group_members_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Домен #3
-- #6
CREATE TABLE IF NOT EXISTS service_packages(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    total_traffic BIGINT NOT NULL,
    total_minutes BIGINT NOT NULL
);
-- #7
CREATE TABLE IF NOT EXISTS group_subscriptions(
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_group_subscriptions_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_group_subscriptions_package_id FOREIGN KEY (package_id) REFERENCES service_packages(id) ON DELETE RESTRICT
);

-- #8
CREATE TABLE IF NOT EXISTS user_limits(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resource_type E_RESOURCE_TYPE NOT NULL,
    limit_value BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_limit_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_limit_per_resource UNIQUE (user_id, resource_type)
);

-- Домен #4
-- #9
CREATE TABLE IF NOT EXISTS group_package_balances(
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    remaining_minutes BIGINT NOT NULL,
    remaining_traffic BIGINT NOT NULL,
    CONSTRAINT fk_group_package_balance_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT uq_group_balance UNIQUE (group_id, period_id)
);

-- #10
CREATE TABLE IF NOT EXISTS user_usage(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    resource_type E_RESOURCE_TYPE NOT NULL,
    used_value BIGINT NOT NULL,
    CONSTRAINT fk_user_usage_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_usage_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_usage UNIQUE (user_id, group_id, period_id, resource_type)
);

-- Домен #5
-- #11
CREATE TABLE IF NOT EXISTS consumption_events(
    event_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    resource_type E_RESOURCE_TYPE NOT NULL,
    amount BIGINT NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_consumption_event_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_consumption_event_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- #12
CREATE TABLE IF NOT EXISTS hourly_usage_stats(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    resource_type E_RESOURCE_TYPE NOT NULL,
    hour_start TIMESTAMP WITH TIME ZONE NOT NULL,
    amount BIGINT NOT NULL,
    CONSTRAINT fk_hourly_usage_stats_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hourly_usage_stats_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- #13
CREATE TABLE IF NOT EXISTS daily_usage_stats(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    resource_type E_RESOURCE_TYPE NOT NULL,
    date DATE NOT NULL,
    amount BIGINT NOT NULL,
    CONSTRAINT fk_daily_usage_stats_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_daily_usage_stats_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- #14
CREATE TABLE IF NOT EXISTS archived_period_usage_stats(
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    total_minutes BIGINT NOT NULL,
    total_traffic BIGINT NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archived_period_usage_stats_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT
);

-- Домен #6
-- #15
CREATE TABLE IF NOT EXISTS notification_thresholds(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    group_id BIGINT,
    resource_type E_RESOURCE_TYPE NOT NULL,
    threshold_percent INT NOT NULL CHECK (threshold_percent BETWEEN 1 AND 100),
    scope_type E_SCOPE_TYPE NOT NULL,
    CONSTRAINT fk_notification_thresholds_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_thresholds_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- #16
CREATE TABLE IF NOT EXISTS notification_logs(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    threshold_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notification_logs_threshold_id FOREIGN KEY (threshold_id) REFERENCES notification_thresholds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_logs_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- #17
CREATE TABLE IF NOT EXISTS audit_logs(
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type E_ACTION_TYPE NOT NULL,
    target_user_id BIGINT,
    details JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_group_id FOREIGN KEY (group_id) REFERENCES family_groups(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_actor_id FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_logs_target_id FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- INDEXES


-- Домен #1
CREATE INDEX IF NOT EXISTS idx_auth_codes_user_id ON auth_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);

-- Домен #2
CREATE INDEX IF NOT EXISTS idx_family_groups_owner_id ON family_groups(owner_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON group_members(user_id);

-- Домен #3
CREATE INDEX IF NOT EXISTS idx_group_subscriptions_group_id ON group_subscriptions(group_id);
CREATE INDEX IF NOT EXISTS idx_group_subscriptions_package_id ON group_subscriptions(package_id);
CREATE INDEX IF NOT EXISTS idx_user_limits_user_id ON user_limits(user_id);

-- Домен #4
CREATE INDEX IF NOT EXISTS idx_group_package_balances_group_id ON group_package_balances(group_id);
CREATE INDEX IF NOT EXISTS idx_user_usage_user_id ON user_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_user_usage_group_id ON user_usage(group_id);

-- Домен #5
CREATE INDEX IF NOT EXISTS idx_consumption_events_user_id ON consumption_events(user_id);
CREATE INDEX IF NOT EXISTS idx_consumption_events_group_id ON consumption_events(group_id);
CREATE INDEX IF NOT EXISTS idx_hourly_usage_stats_user_id ON hourly_usage_stats(user_id);
CREATE INDEX IF NOT EXISTS idx_hourly_usage_stats_group_id ON hourly_usage_stats(group_id);
CREATE INDEX IF NOT EXISTS idx_daily_usage_stats_user_id ON daily_usage_stats(user_id);
CREATE INDEX IF NOT EXISTS idx_daily_usage_stats_group_id ON daily_usage_stats(group_id);
CREATE INDEX IF NOT EXISTS idx_archived_period_stats_group_id ON archived_period_usage_stats(group_id);

-- Домен #6
CREATE INDEX IF NOT EXISTS idx_notification_thresholds_group_id ON notification_thresholds(group_id);
CREATE INDEX IF NOT EXISTS idx_notification_thresholds_user_id ON notification_thresholds(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_logs_user_id ON notification_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_logs_threshold_id ON notification_logs(threshold_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_group_id ON audit_logs(group_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_user_id ON audit_logs(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_user_id ON audit_logs(target_user_id);

-- Остальные

CREATE INDEX IF NOT EXISTS idx_consumption_events_unprocessed 
    ON consumption_events(event_timestamp) 
    WHERE processed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_auth_codes_active 
    ON auth_codes(user_id, code) 
    WHERE is_used = FALSE;

CREATE INDEX IF NOT EXISTS idx_user_sessions_active 
    ON user_sessions(user_id, access_token) 
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_hourly_stats_lookup 
    ON hourly_usage_stats(user_id, resource_type, hour_start);

CREATE INDEX IF NOT EXISTS idx_daily_stats_lookup 
    ON daily_usage_stats(group_id, resource_type, date);

CREATE INDEX IF NOT EXISTS idx_audit_logs_group_timeline 
    ON audit_logs(group_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_group_subscriptions_active_period 
    ON group_subscriptions(group_id, period_start, period_end);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_owner ON family_groups(owner_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_group_member ON group_members(user_id) WHERE status = 'ACTIVE';