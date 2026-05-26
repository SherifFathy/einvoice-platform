DO $$
DECLARE
    v_email TEXT := '${BOOTSTRAP_SUPERUSER_EMAIL}';
    v_hash  TEXT := '${BOOTSTRAP_SUPERUSER_PASSWORD_HASH}';
BEGIN
    IF v_email IS NOT NULL AND v_email <> '' AND v_email <> 'REPLACE_WITH_SUPERUSER_EMAIL'
       AND v_hash IS NOT NULL AND v_hash <> '' AND v_hash <> 'REPLACE_WITH_BCRYPT_HASH'
    THEN
        INSERT INTO users (id, name, email, password_hash, is_super_user, is_active)
        VALUES (
            gen_random_uuid(),
            'Super User',
            v_email,
            v_hash,
            TRUE,
            TRUE
        ) ON CONFLICT (email) DO NOTHING;
        RAISE NOTICE 'Bootstrap Super User seeded for %', v_email;
    ELSE
        RAISE WARNING 'Bootstrap Super User skipped: BOOTSTRAP_SUPERUSER_EMAIL and/or BOOTSTRAP_SUPERUSER_PASSWORD_HASH not set';
    END IF;
END
$$;
