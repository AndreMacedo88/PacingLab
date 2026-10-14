#!/usr/bin/env bash
# Installs the project-local toolchain (JDK + Android SDK) into .toolchain/.
#
# Everything lands inside the repository folder (ignored by git), so removing
# .toolchain/ and ~/.gradle/ uninstalls the whole development setup.
#
# Usage: tools/bootstrap.sh [--with-emulator]
#
# The script is idempotent: re-running it only installs what is missing.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="$ROOT/.toolchain"
DOWNLOADS="$TOOLCHAIN/downloads"

# Pinned versions. Bump these deliberately and update the checksums with them.
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
JDK_SHA256="ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"

SDK_PACKAGES=(
  "platform-tools"
  "platforms;android-37.0"
  "build-tools;37.0.0"
)
EMULATOR_PACKAGES=(
  "emulator"
  "system-images;android-37.0;google_apis;x86_64"
)
AVD_NAME="pacinglab_api37"

with_emulator=false
for arg in "$@"; do
  case "$arg" in
    --with-emulator) with_emulator=true ;;
    -h | --help)
      sed -n '2,10p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

download() {
  local url=$1 dest=$2 algo=$3 expected=$4
  if [[ ! -f "$dest" ]]; then
    log "Downloading $(basename "$dest")"
    curl -fL --retry 3 --progress-bar -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
  fi
  local actual
  actual=$("${algo}sum" "$dest" | cut -d' ' -f1)
  if [[ "$actual" != "$expected" ]]; then
    echo "Checksum mismatch for $dest (expected $expected, got $actual)" >&2
    rm -f "$dest"
    exit 1
  fi
}

mkdir -p "$DOWNLOADS"

# JDK
if [[ ! -x "$TOOLCHAIN/jdk/bin/java" ]]; then
  archive="$DOWNLOADS/jdk.tar.gz"
  download "$JDK_URL" "$archive" sha256 "$JDK_SHA256"
  log "Unpacking JDK"
  rm -rf "$TOOLCHAIN/jdk"
  mkdir -p "$TOOLCHAIN/jdk"
  tar -xzf "$archive" -C "$TOOLCHAIN/jdk" --strip-components=1
fi

# Android command-line tools
export ANDROID_HOME="$TOOLCHAIN/android-sdk"
export ANDROID_USER_HOME="$TOOLCHAIN/android-user"
export JAVA_HOME="$TOOLCHAIN/jdk"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  archive="$DOWNLOADS/cmdline-tools.zip"
  download "$CMDLINE_TOOLS_URL" "$archive" sha1 "$CMDLINE_TOOLS_SHA1"
  log "Unpacking Android command-line tools"
  tmp="$(mktemp -d)"
  unzip -q "$archive" -d "$tmp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi
mkdir -p "$ANDROID_USER_HOME"

packages=("${SDK_PACKAGES[@]}")
if $with_emulator; then
  packages+=("${EMULATOR_PACKAGES[@]}")
fi

log "Accepting SDK licences"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

log "Installing SDK packages: ${packages[*]}"
"$SDKMANAGER" --install "${packages[@]}" | grep -v '^\[=*' || true

if $with_emulator && ! "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$AVD_NAME"; then
  log "Creating emulator $AVD_NAME"
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
    --name "$AVD_NAME" \
    --package "system-images;android-37.0;google_apis;x86_64" \
    --device "pixel_8"
fi

# Point Gradle at the SDK. local.properties is machine-specific and ignored by git.
props="$ROOT/local.properties"
if ! grep -q '^sdk.dir=' "$props" 2>/dev/null; then
  echo "sdk.dir=$ANDROID_HOME" >>"$props"
fi

log "Toolchain ready. Run 'source tools/env.sh' to use it in your shell."
