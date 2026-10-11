#!/bin/bash
# Builds the shop release for the Windows laptop (run on the Mac):
#   1. builds the frontend (same-origin API, see frontendelramy/.env.production)
#   2. puts it inside the backend jar (src/main/resources/static, git-ignored)
#   3. packages elramy.jar + the Windows scripts into release/Elramy-<date>.zip
# Usage: ElramyBusiness/deploy/build-release.sh   (tests: run ./mvnw test separately)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$(cd "$HERE/.." && pwd)"
FRONTEND="$(cd "$BACKEND/../frontendelramy" && pwd)"
OUT="$(cd "$BACKEND/.." && pwd)/release"
STAMP="$(date +%Y-%m-%d_%H%M)"
STATIC="$BACKEND/src/main/resources/static"

echo "==> Frontend build"
(cd "$FRONTEND" && npm run -s typecheck && npm run -s build)

echo "==> Backend jar with the frontend inside"
rm -rf "$STATIC" && mkdir -p "$STATIC" && cp -R "$FRONTEND/dist/." "$STATIC/"
(cd "$BACKEND" && ./mvnw -q clean package -DskipTests)
rm -rf "$STATIC"   # keep development (Vite on :5173) unaffected

JAR="$(ls "$BACKEND"/target/MafroshartElramyz-*.jar | grep -v plain | head -1)"
PKG="$OUT/Elramy-$STAMP"
rm -rf "$PKG" && mkdir -p "$PKG"
cp "$JAR" "$PKG/elramy.jar"
cp "$HERE"/windows/* "$PKG/"
(cd "$BACKEND" && git rev-parse --short HEAD > "$PKG/VERSION.txt")
(cd "$OUT" && rm -f "Elramy-$STAMP.zip" && zip -qr "Elramy-$STAMP.zip" "Elramy-$STAMP")
echo "==> $OUT/Elramy-$STAMP.zip"
