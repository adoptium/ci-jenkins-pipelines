#!/bin/bash

###################################################################
# Script to build jtharness reusable by jdk testing community     #
# currently builds tip and latest released version                #
###################################################################

# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function hashArtifacts() {
  echo "Creating checksums all jtharness*.jar"
  for file in `ls javatest*.jar` ; do
    sha256sum $file > $file.sha256sum.txt
  done
}

function detectJdks() {
  jvm_dir="/usr/lib/jvm/"
  find ${jvm_dir} -maxdepth 1 | sort
  echo "Available jdks 11 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11
  jdk11=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11 | head -n 1))
}

REPO_DIR="jtharness"
BUILD_PATH=JTHarness-build/binaries/lib
main_file=javatest
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
else
  rm -vf $REPO_DIR/$main_file*.jar
fi
detectJdks
pushd $REPO_DIR
  git checkout master
  rm -rf ../$BUILD_PATH
  tip=`git log | head -n 1 | sed "s/.*\s\+//"` || true
  tip_shortened=`echo ${tip:0:10}`
  latestRelease=`git tag -l | sort -Vr | head -n 1`
  rc=$main_file-$latestRelease

  # latest released
  git checkout $latestRelease
  export JAVA_HOME=$jdk11
  pushd build
    ant test | tee ../$rc.jar.txt || true
    ant build
  popd
  mv  ../$BUILD_PATH/$main_file.jar $rc.jar
  echo "Manually renaming $rc.jar as $main_file.jar to provide latest-stable-recommended file"
  ln -sfv $rc.jar $main_file.jar
  pushd build
    ant clean
  popd
  rm -rf ../$BUILD_PATH

  # tip
  git checkout master
  export JAVA_HOME=$jdk11
  pushd build
    ant test | tee ../$main_file-$tip_shortened.jar.txt || true
    ant build
  popd
  mv  ../$BUILD_PATH/$main_file.jar $main_file-$tip_shortened.jar
  echo "Manually renaming $main_file-$tip_shortened.jar as $main_file-tip.jar to provide latest-unstable-recommended file"
  ln -sfv $main_file-$tip_shortened.jar $main_file-tip.jar
  pushd build
    ant clean
  popd
  rm -rf ../$BUILD_PATH

  echo "Resetting repo back to master"
  git checkout master
  hashArtifacts
popd
