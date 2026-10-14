# Source this file to put the project-local toolchain on PATH:
#
#   source tools/env.sh
#
# VS Code's integrated terminal and tasks set the same variables automatically
# (see .vscode/settings.json), so this is only needed in other terminals.

PACINGLAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
export PACINGLAB_ROOT
export JAVA_HOME="$PACINGLAB_ROOT/.toolchain/jdk"
export ANDROID_HOME="$PACINGLAB_ROOT/.toolchain/android-sdk"
export ANDROID_USER_HOME="$PACINGLAB_ROOT/.toolchain/android-user"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
