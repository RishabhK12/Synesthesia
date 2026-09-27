#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export GRADLE_USER_HOME="$PWD/.gradle-user"
variant=StandardDebug
flavor=standard
output=BehindAlert.apk
test_task=()
for arg in "$@"; do
  case "$arg" in
    --side-by-side) variant=SideBySideDebug; flavor=sideBySide; output=SoundDirectionTest.apk ;;
    --tests) test_task=(yes) ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done
if ((${#test_task[@]})); then test_task=("test${variant}UnitTest"); fi
bash ./gradlew "assemble${variant}" "${test_task[@]}" --no-daemon
cp "build/outputs/apk/$flavor/debug/"*.apk "build/$output"
echo "APK ready: build/$output"
