#!/usr/bin/env bash
# A narrated walk through the team-notes-service REST API: three seeded users, one note, every rule.
# Usage: bin/demo.sh            (starts the service from service/target if nothing answers on $NOTES_URL)
#        NOTES_URL=http://host:port bin/demo.sh
#        bin/demo.sh --quiet    (statuses and one-line results only)
set -euo pipefail

URL="${NOTES_URL:-http://localhost:8080}"
QUIET="${1:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/service/target/team-notes-service.jar"
STARTED_PID=""
TMP="$(mktemp -d)"
trap 'if [ -n "$STARTED_PID" ]; then kill "$STARTED_PID" 2>/dev/null || true; fi; rm -rf "$TMP"' EXIT

# The jar is built for Java 25. Prefer $JAVA_HOME, then a `java` on PATH that is 25+, then an sdkman JDK 25.
pick_java() {
  local candidate
  for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java || true)" "$HOME"/.sdkman/candidates/java/25*/bin/java; do
    [ -n "$candidate" ] && [ -x "$candidate" ] || continue
    local major
    major=$("$candidate" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1)
    if [ "${major:-0}" -ge 25 ]; then echo "$candidate"; return 0; fi
  done
  return 1
}

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
step() { printf '\n'; bold "── $*"; }
say()  { printf '   %s\n' "$*"; }

# call <label> <user:pw|-> <method> <path> [curl args...]  → prints status + pretty body, sets $STATUS and $BODY
call() {
  local label="$1" auth="$2" method="$3" path="$4"; shift 4
  local authflag=""
  [ "$auth" != "-" ] && authflag="-u $auth"
  say "$label"
  say "\$ curl -X $method ${authflag:+$authflag }$URL$path $*"
  # shellcheck disable=SC2086
  STATUS=$(curl -s -o "$TMP/body" -w '%{http_code}' $authflag -X "$method" "$URL$path" "$@" || echo 000)
  BODY=$(cat "$TMP/body")
  say "→ HTTP $STATUS"
  if [ "$QUIET" != "--quiet" ] && [ -n "$BODY" ]; then
    (python3 -m json.tool "$TMP/body" 2>/dev/null || cat "$TMP/body") | sed 's/^/     /' | head -40
  fi
}
json() { python3 -c "import sys,json; d=json.load(open('$TMP/body')); print($1)"; }

# ---------------------------------------------------------------- 0. the service
step "0. The service"
if ! curl -sf "$URL/actuator/health" >/dev/null 2>&1; then
  if [ ! -f "$JAR" ]; then
    say "nothing answers at $URL and $JAR is not built; run: ./mvnw -q -pl service -DskipTests package"; exit 1
  fi
  JAVA="$(pick_java)" || { say "the service needs Java 25; set JAVA_HOME to a JDK 25 (found: $(java -version 2>&1 | head -1))"; exit 1; }
  say "nothing answers at $URL; starting $JAR with $JAVA"
  (cd "$ROOT/service" && exec "$JAVA" -jar "$JAR" > "$TMP/service.log" 2>&1) &
  STARTED_PID=$!
  for _ in $(seq 1 60); do curl -sf "$URL/actuator/health" >/dev/null 2>&1 && break; sleep 1; done
  if ! curl -sf "$URL/actuator/health" >/dev/null 2>&1; then
    say "the service did not come up; last lines of its log:"; tail -20 "$TMP/service.log" | sed 's/^/     /'; exit 1
  fi
fi
call "Health is public." - GET /actuator/health
call "Everything under /api needs HTTP Basic; errors are RFC 9457 problem details." - GET /api/me
say "Seeded users: alice (admin of team platform, also service admin), bob (platform member, data admin), carol (data member)."

# ---------------------------------------------------------------- 1. who am I
step "1. Who am I, and which teams am I in"
call "alice signs in." alice:alice-pw GET /api/me
PLATFORM=$(json '[t["id"] for t in d["teams"] if t["name"]=="platform"][0]')
say "team platform = $PLATFORM"

# ---------------------------------------------------------------- 2. a team note
step "2. alice writes a Markdown note visible to team platform"
python3 - "$PLATFORM" > "$TMP/note.json" <<'PY'
import json,sys
print(json.dumps({"title":"Heron sightings","body":"# Log\n\nTwo herons at the *pond* this morning.",
  "bodyFormat":"MARKDOWN","tags":["birds","pond"],"visibility":"TEAM","teamId":sys.argv[1]}))
PY
call "POST creates it; the Location header points at it, version starts at 1." alice:alice-pw POST /api/notes -H 'Content-Type: application/json' -d @"$TMP/note.json"
NOTE=$(json 'd["id"]')
say "note = $NOTE"

# ---------------------------------------------------------------- 3. visibility
step "3. Visibility: members see it, outsiders get 404 (not 403, so existence never leaks)"
call "bob is a platform member." bob:bob-pw GET "/api/notes/$NOTE"
call "carol is not; the note does not exist for her." carol:carol-pw GET "/api/notes/$NOTE"

# ---------------------------------------------------------------- 4. the whiteboard
step "4. The whiteboard: anyone who can see the note appends entries; each entry has its own author and version"
call "bob appends an entry (Markdown)." bob:bob-pw POST "/api/notes/$NOTE/entries" -H 'Content-Type: application/json' -d '{"body":"saw a **third** one at noon","bodyFormat":"MARKDOWN"}'
ENTRY=$(json 'd["id"]')
call "alice appends one too; appends never conflict." alice:alice-pw POST "/api/notes/$NOTE/entries" -H 'Content-Type: application/json' -d '{"body":"bringing binoculars tomorrow"}'
call "alice cannot edit bob's entry (403)..." alice:alice-pw PUT "/api/notes/$NOTE/entries/$ENTRY" -H 'Content-Type: application/json' -d '{"body":"x","version":1}'
call "...but bob can, with the version he read." bob:bob-pw PUT "/api/notes/$NOTE/entries/$ENTRY" -H 'Content-Type: application/json' -d '{"body":"saw a **third** one at noon, by the reeds","bodyFormat":"MARKDOWN","version":1}'
call "The same edit again with the stale version is a 409 carrying the current version." bob:bob-pw PUT "/api/notes/$NOTE/entries/$ENTRY" -H 'Content-Type: application/json' -d '{"body":"again","version":1}'

# ---------------------------------------------------------------- 5. the owner's fields
step "5. Only the owner changes the note itself, and only with the version they read"
call "bob may read but not edit (403)." bob:bob-pw PUT "/api/notes/$NOTE" -H 'Content-Type: application/json' -d '{"title":"x","body":"y","visibility":"PRIVATE","version":1}'
call "alice with a stale version: 409 + currentVersion." alice:alice-pw PUT "/api/notes/$NOTE" -H 'Content-Type: application/json' -d '{"title":"Heron sightings","body":"now org wide","visibility":"ORGANIZATION","tags":["birds"],"version":7}'
call "alice with version 1: the note becomes ORGANIZATION-wide, version 2." alice:alice-pw PUT "/api/notes/$NOTE" -H 'Content-Type: application/json' -d '{"title":"Heron sightings","body":"Two herons at the pond; everyone welcome to add sightings.","visibility":"ORGANIZATION","tags":["birds","pond"],"version":1}'
call "carol can read it now, with entry and attachment counts." carol:carol-pw GET "/api/notes/$NOTE"

# ---------------------------------------------------------------- 6. attachments
step "6. Attachments: stored by content, their text indexed for search"
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>endobj\n4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n5 0 obj<</Length 60>>stream\nBT /F1 14 Tf 72 700 Td (Kestrel migration budget: 42 feathers) Tj ET\nendstream\nendobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' > "$TMP/memo.pdf"
call "bob uploads a one-page PDF (multipart part 'file')." bob:bob-pw POST "/api/notes/$NOTE/attachments" -F "file=@$TMP/memo.pdf;type=application/pdf"
ATT=$(json 'd["id"]'); SHA=$(json 'd["sha256"]')
call "An .exe is refused with 415 before any bytes are kept." bob:bob-pw POST "/api/notes/$NOTE/attachments" -F "file=@$TMP/memo.pdf;type=application/x-msdownload"
say "Download it back; the ETag is the sha256 of the bytes."
curl -s -u carol:carol-pw -D "$TMP/h" -o "$TMP/dl.pdf" "$URL/api/notes/$NOTE/attachments/$ATT"
grep -iE '^(HTTP|ETag|Content-Disposition|Content-Length)' "$TMP/h" | sed 's/^/     /'
LOCAL=$(python3 -c "import hashlib,sys; print(hashlib.sha256(open('$TMP/dl.pdf','rb').read()).hexdigest())")
say "server sha256 $SHA"; say "local  sha256 $LOCAL"; [ "$SHA" = "$LOCAL" ] && say "✓ byte-identical" || say "✗ MISMATCH"

# ---------------------------------------------------------------- 7. search
step "7. Search across teams (Stack Overflow style): everything the caller may see, ranked"
call "carol searches a word that only exists inside the PDF." carol:carol-pw GET "/api/search?q=kestrel"
call "Title matches rank above body matches; results carry a score and a snippet." carol:carol-pw GET "/api/search?q=heron&sort=RELEVANCE"
call "Filters: by tag, newest first." carol:carol-pw GET "/api/search?tag=birds&sort=NEWEST"
call "Related notes (more-like-this over title, body, entries, attachments, tags), visibility-filtered." carol:carol-pw GET "/api/notes/$NOTE/related"

# ---------------------------------------------------------------- 8. live stream
step "8. The live stream: Server-Sent Events per note"
say "bob subscribes to $URL/api/notes/$NOTE/events in the background; carol appends an entry."
curl -s -N -u bob:bob-pw "$URL/api/notes/$NOTE/events" > "$TMP/sse" 2>/dev/null &
SSE_PID=$!; disown $SSE_PID 2>/dev/null || true
sleep 1
curl -s -o /dev/null -u carol:carol-pw -H 'Content-Type: application/json' -d '{"body":"a heron just flew past the data team window"}' "$URL/api/notes/$NOTE/entries"
sleep 1; kill $SSE_PID 2>/dev/null || true
say "bob's stream received:"; sed 's/^/     /' "$TMP/sse"

# ---------------------------------------------------------------- 9. rendering
step "9. Rendered HTML: the note and its entries, Markdown rendered, raw HTML escaped"
say "\$ curl -u carol:carol-pw $URL/api/notes/$NOTE/html"
curl -s -u carol:carol-pw "$URL/api/notes/$NOTE/html" | sed 's/^/     /'

# ---------------------------------------------------------------- 10. archive
step "10. Archive makes a note read-only; unarchive reopens it"
call "alice archives (idempotent; version bumps once)." alice:alice-pw POST "/api/notes/$NOTE/archive"
call "bob can no longer append (403)." bob:bob-pw POST "/api/notes/$NOTE/entries" -H 'Content-Type: application/json' -d '{"body":"too late"}'
call "Archived notes drop out of lists and search unless asked for." alice:alice-pw GET "/api/notes?tag=birds"
call "alice unarchives." alice:alice-pw POST "/api/notes/$NOTE/unarchive"

# ---------------------------------------------------------------- 11. teams
step "11. Teams: admins manage members; the last admin can be neither demoted nor removed"
call "bob (member, not admin) cannot add carol to platform." bob:bob-pw PUT "/api/teams/$PLATFORM/members/carol" -H 'Content-Type: application/json' -d '{"role":"MEMBER"}'
call "alice (admin) can." alice:alice-pw PUT "/api/teams/$PLATFORM/members/carol" -H 'Content-Type: application/json' -d '{"role":"MEMBER"}'
call "alice tries to demote herself, the only admin: 409." alice:alice-pw PUT "/api/teams/$PLATFORM/members/alice" -H 'Content-Type: application/json' -d '{"role":"MEMBER"}'
call "carol leaves the team herself (a member may remove themselves)." carol:carol-pw DELETE "/api/teams/$PLATFORM/members/carol"
call "Members now." alice:alice-pw GET "/api/teams/$PLATFORM/members"

# ---------------------------------------------------------------- 12. validation and paging
step "12. Validation comes from the core's guards, paging is keyset"
call "A blank title and a bad tag: 400 with field errors." alice:alice-pw POST /api/notes -H 'Content-Type: application/json' -d '{"title":" ","body":"b","visibility":"PRIVATE","tags":["Bad Tag"]}'
call "A malformed cursor: 400." alice:alice-pw GET "/api/notes?cursor=junk"
call "alice's notes, one per page; nextCursor continues from the last item." alice:alice-pw GET "/api/notes?limit=1"

# ---------------------------------------------------------------- 13. admin and cleanup
step "13. Admin reindex, then cleanup"
call "alice is in notes.admin-users: rebuild the search index from the store." alice:alice-pw POST /api/admin/reindex
call "bob is not: 403." bob:bob-pw POST /api/admin/reindex
call "alice deletes the note; entries, attachments and the index entry go with it." alice:alice-pw DELETE "/api/notes/$NOTE"
call "Gone." alice:alice-pw GET "/api/notes/$NOTE"

printf '\n'; bold "Done. Swagger UI: $URL/swagger-ui.html"
