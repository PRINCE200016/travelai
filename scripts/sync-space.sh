#!/usr/bin/env bash
# Copy the backend into the Hugging Face Space repo (TripMindAi/) before deploying.
#
#   bash scripts/sync-space.sh
#
# What it does:
#   - replaces TripMindAi/src and TripMindAi/pom.xml with backend/src and backend/pom.xml
#   - keeps TripMindAi's own files: Dockerfile, README.md (Space settings), .gitignore and
#     RootController.java (answers "/" with a status message, since the Space has no website)
#   - prints the commands to commit and push (pushing to Hugging Face redeploys the live API)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPACE="$ROOT/TripMindAi"
KEEP="src/main/java/com/tripmind/controller/RootController.java"

[ -d "$SPACE/.git" ] || { echo "TripMindAi/ is not a git repo. Clone the Space first:"; echo "  git clone https://huggingface.co/spaces/Arjunrajawat/TripMindAi TripMindAi"; exit 1; }

TMP="$(mktemp -d)"
cp "$SPACE/$KEEP" "$TMP/RootController.java"

rm -rf "$SPACE/src"
cp -r "$ROOT/backend/src" "$SPACE/src"
cp "$ROOT/backend/pom.xml" "$SPACE/pom.xml"
cp "$TMP/RootController.java" "$SPACE/$KEEP"
rm -rf "$TMP"

echo "Synced backend -> TripMindAi. Differences (RootController is expected):"
diff -rq "$ROOT/backend/src" "$SPACE/src" || true
echo
echo "Next:"
echo "  cd TripMindAi && git add -A && git commit -m \"<what changed>\" && git push origin main"
echo "  (Hugging Face rebuilds in ~2-5 minutes; check https://arjunrajawat-tripmindai.hf.space/actuator/health)"
