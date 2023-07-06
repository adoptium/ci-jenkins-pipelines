#!/bin/bash
set -eu

echo 'Starting build process...'
export WORKSPACE="$WORKSPACE/temurin-sbom/cyclonedx-lib"
cd "$WORKSPACE"
export JAVA_HOME=/usr/lib/jvm/jdk-17
ant -f build.xml clean
ant -f build.xml build-sign-sbom
ant -f build.xml build
