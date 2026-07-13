#!/bin/bash

GRADLE_VERSION=8.10.2
WRAPPER_VERSION=8.10.2

# 下载 Gradle Wrapper jar
wget -q "https://github.com/gradle/gradle/raw/v$WRAPPER_VERSION/gradle/wrapper/gradle-wrapper.jar" \
    -O "gradle/wrapper/gradle-wrapper.jar"

if [ $? -eq 0 ]; then
    echo "Gradle wrapper jar downloaded successfully"
else
    echo "Failed to download Gradle wrapper jar"
    exit 1
fi
