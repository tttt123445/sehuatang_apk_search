#!/bin/sh
set -eu

GRADLE_VERSION="9.4.1"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin/codex"
GRADLE_HOME="$DIST_DIR/gradle-${GRADLE_VERSION}"
ZIP_FILE="$DIST_DIR/gradle-${GRADLE_VERSION}-bin.zip"
DEFAULT_JBR="/Applications/GoLand.app/Contents/jbr/Contents/Home"

if [ -z "${JAVA_HOME:-}" ] && [ -x "$DEFAULT_JBR/bin/java" ]; then
  JAVA_HOME="$DEFAULT_JBR"
  export JAVA_HOME
fi

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -L "$DIST_URL" -o "$ZIP_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP_FILE" "$DIST_URL"
    else
      echo "curl or wget is required to download Gradle ${GRADLE_VERSION}" >&2
      exit 1
    fi
  fi
  unzip -q "$ZIP_FILE" -d "$DIST_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
