-- Cassandra data model for team-notes-service (design artifact; the Cassandra adapter is on the roadmap, see README).
-- Query-first: one table per access pattern, full copies in the lookup tables, no joins, no secondary indexes.

CREATE KEYSPACE IF NOT EXISTS notes WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS notes.users_by_username (
  username text PRIMARY KEY, password_hash text, display_name text, created_at timestamp);

CREATE TABLE IF NOT EXISTS notes.teams_by_id (
  team_id uuid PRIMARY KEY, name text, created_at timestamp);

-- memberships are written to both tables in one logged batch
CREATE TABLE IF NOT EXISTS notes.memberships_by_team (
  team_id uuid, username text, role text, PRIMARY KEY ((team_id), username));
CREATE TABLE IF NOT EXISTS notes.memberships_by_user (
  username text, team_id uuid, role text, PRIMARY KEY ((username), team_id));

-- source of truth and the lightweight-transaction gate: UPDATE ... IF version = ?
CREATE TABLE IF NOT EXISTS notes.notes_by_id (
  note_id uuid PRIMARY KEY, owner text, team_id uuid, title text, body text, body_format text, tags set<text>,
  visibility text, archived boolean, version bigint, created_at timestamp, updated_at timestamp);

-- one partition per owner, newest first; the keyset cursor is (created_at, note_id)
CREATE TABLE IF NOT EXISTS notes.notes_by_owner (
  owner text, created_at timestamp, note_id uuid, team_id uuid, title text, body text, body_format text, tags set<text>,
  visibility text, archived boolean, version bigint, updated_at timestamp,
  PRIMARY KEY ((owner), created_at, note_id)) WITH CLUSTERING ORDER BY (created_at DESC, note_id DESC);

-- only TEAM-visibility notes live here; a visibility change deletes the old row in the same batch
CREATE TABLE IF NOT EXISTS notes.notes_by_team (
  team_id uuid, created_at timestamp, note_id uuid, owner text, title text, body text, body_format text, tags set<text>,
  archived boolean, version bigint, updated_at timestamp,
  PRIMARY KEY ((team_id), created_at, note_id)) WITH CLUSTERING ORDER BY (created_at DESC, note_id DESC);

-- whiteboard entries: appends never conflict; each row carries its own version for UPDATE ... IF version = ?
CREATE TABLE IF NOT EXISTS notes.entries_by_note (
  note_id uuid, created_at timestamp, entry_id uuid, author text, body text, body_format text, version bigint, updated_at timestamp,
  PRIMARY KEY ((note_id), created_at, entry_id));

CREATE TABLE IF NOT EXISTS notes.attachments_by_note (
  note_id uuid, created_at timestamp, attachment_id uuid, filename text, content_type text, size bigint, sha256 text,
  uploaded_by text, extracted_text text, chunk_count int,
  PRIMARY KEY ((note_id), created_at, attachment_id));

-- attachment bytes in 256 KB chunks; the 10 MB default limit keeps a partition small
CREATE TABLE IF NOT EXISTS notes.attachment_chunks (
  attachment_id uuid, chunk_no int, data blob, PRIMARY KEY ((attachment_id), chunk_no));

-- Example reads
-- SELECT * FROM notes.notes_by_owner WHERE owner = ? AND (created_at, note_id) < (?, ?) AND archived = false LIMIT ? ALLOW FILTERING;
--   (ALLOW FILTERING restricted to one partition is a scan of that owner's rows only; fine for small teams,
--    a notes_by_owner_tag table is the upgrade path if tag queries become hot)
-- UPDATE notes.notes_by_id SET title = ?, ..., version = ? WHERE note_id = ? IF version = ?;
--   then BEGIN BATCH upsert notes_by_owner / notes_by_team rows APPLY BATCH
