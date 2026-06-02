#!/bin/sh
# Gradle wrapper script pour Linux/Mac

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
exec "$APP_HOME/gradle/wrapper/gradle-wrapper" "$@"
