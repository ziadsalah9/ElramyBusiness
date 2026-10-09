#!/bin/bash
# Builds the one-file Windows installer release/Elramy-Setup-<version>.exe (run on the Mac).
# It bundles everything the laptop needs, so nothing has to be installed by hand:
#   the store (jar with the web app inside), Java 21 JRE (Temurin), PostgreSQL 16.
# Needs: Docker (Inno Setup runs in the amake/innosetup image) and the runtime zips in
# .local-dev/installer-cache (downloaded once, checked against their .sha256 files).
# Usage: ElramyBusiness/deploy/build-installer.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
BACKEND="$(cd "$HERE/.." && pwd)"
SHOP="$(cd "$BACKEND/.." && pwd)"
CACHE="$SHOP/.local-dev/installer-cache"
JRE_ZIP="$CACHE/OpenJDK21U-jre_x64_windows_hotspot_21.0.12.1_1.zip"
PG_ZIP="$CACHE/postgresql-16.15-1-windows-x64-binaries.zip"
WORK="$SHOP/.local-dev/installer-build"
VERSION="1.0.$(date +%y%m).$(date +%d%H)"

for z in "$JRE_ZIP" "$PG_ZIP"; do
  [ -f "$z" ] || { echo "Missing $z" >&2; exit 1; }
  echo "$(cat "$z.sha256" | awk '{print $1}')  $z" | shasum -a 256 -c --quiet -
done

echo "==> Release build (frontend + jar)"
"$HERE/build-release.sh"
PKG="$(ls -d "$SHOP"/release/Elramy-20*/ | sort | tail -1)"

echo "==> Staging"
rm -rf "$WORK" && mkdir -p "$WORK/stage/app" "$WORK/stage/jre" "$WORK/stage/pgsql" "$WORK/unzip"
cp -R "$PKG"/. "$WORK/stage/app/"
# Setup.exe itself installs, upgrades and uninstalls; the manual-kit scripts stay in the zip only.
rm -f "$WORK"/stage/app/{install,update,uninstall}.{ps1,bat}
unzip -q "$JRE_ZIP" -d "$WORK/unzip/jre"
cp -R "$WORK"/unzip/jre/*/. "$WORK/stage/jre/"
# Only the server and client tools: no pgAdmin, StackBuilder, docs or headers.
unzip -q "$PG_ZIP" -d "$WORK/unzip" -x 'pgsql/pgAdmin*' 'pgsql/StackBuilder*' 'pgsql/doc/*' 'pgsql/include/*'
cp -R "$WORK"/unzip/pgsql/. "$WORK/stage/pgsql/"
rm -rf "$WORK/unzip"
for f in stage/jre/bin/java.exe stage/pgsql/bin/initdb.exe stage/pgsql/bin/pg_ctl.exe stage/pgsql/bin/pg_dump.exe stage/pgsql/bin/pg_restore.exe stage/pgsql/bin/psql.exe stage/app/elramy.jar stage/app/setup-elramy.ps1; do
  [ -f "$WORK/$f" ] || { echo "Staging incomplete: $f" >&2; exit 1; }
done
cp "$HERE/installer/elramy.iss" "$WORK/"

echo "==> Compiling the installer ($VERSION)"
docker run --rm -v "$WORK:/work" amake/innosetup:innosetup6 "/DAppVersion=$VERSION" elramy.iss > "$WORK/iscc.log" 2>&1 \
  || { tail -30 "$WORK/iscc.log" >&2; exit 1; }
mkdir -p "$SHOP/release"
OUTFILE="$SHOP/release/Elramy-Setup-$VERSION.exe"
cp "$WORK/out/Elramy-Setup-$VERSION.exe" "$OUTFILE"
(cd "$SHOP/release" && shasum -a 256 "$(basename "$OUTFILE")" > "$(basename "$OUTFILE").sha256")
rm -rf "$WORK/stage"
echo "==> $OUTFILE ($(du -h "$OUTFILE" | cut -f1))"
