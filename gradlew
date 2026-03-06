#!/bin/sh
# Gradle wrapper for CI/CD
set -e
DIRNAME=$(dirname "$0")
APP_HOME=$DIRNAME
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
