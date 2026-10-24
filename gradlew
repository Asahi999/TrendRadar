#!/bin/sh
# 仓库根目录入口：转到微信群发工程再执行 Gradle。
ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
cd "$ROOT/wechat-broadcast" || exit 1
exec ./gradlew "$@"
