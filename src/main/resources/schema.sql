-- Runs on every start, after Hibernate has created the tables (see spring.jpa.defer-datasource-initialization).
-- Hibernate doesn't create multi-column unique constraints on SQLite, so they're added here.
-- IF NOT EXISTS makes this safe to rerun; it works on SQLite and PostgreSQL.
create unique index if not exists ux_user_roles_user_role on user_roles (user_id, role_id);
create unique index if not exists ux_file_storage_file_part on file_storage (file_id, part_no);
