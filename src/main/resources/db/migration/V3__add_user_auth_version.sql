ALTER TABLE sys_user
    ADD COLUMN auth_version INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Invalidates previously issued access tokens' AFTER status;
