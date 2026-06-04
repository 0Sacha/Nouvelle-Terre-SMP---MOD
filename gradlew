#!/bin/sh
#
# Gradle wrapper script (Unix/Mac/Linux/Git Bash)
#

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAVA_OPTS="${JAVA_OPTS:-}"

exec java $JAVA_OPTS \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
