#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
NOTES="${2:-}"
if [[ -z "$VERSION" || -z "$NOTES" ]]; then
  echo "usage: ./scripts/release.sh <version-without-v> \"release notes\"" >&2
  echo "example: ./scripts/release.sh 1.0.1 \"修复列表门锁提示\"" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f keystore.properties ]]; then
  echo "缺少 keystore.properties；请先准备签名密钥。" >&2
  exit 1
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "工作区有未提交改动；请先提交，保证 release 对应源码。" >&2
  exit 1
fi

TAG="v$VERSION"
mkdir -p dist
./gradlew :app:assembleRelease

APK="app/build/outputs/apk/release/app-release.apk"
OUT="dist/louji-$TAG.apk"
cp "$APK" "$OUT"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUT" > "$OUT.sha256"
else
  shasum -a 256 "$OUT" > "$OUT.sha256"
fi

gh release create "$TAG" \
  "$OUT" \
  "$OUT.sha256" \
  --title "楼记 $TAG" \
  --notes "$NOTES" \
  --latest

echo "Released $TAG"
