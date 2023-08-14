#!/bin/bash

###################################################################
# Script to build sigtest reusable by jdk testing community       #
# currently builds tip and latest released version                #
###################################################################

# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function hashArtifacts() {
  echo "Creating checksums all sigtest*.zip"
  for file in `ls sigtest*.zip` ; do
    sha256sum $file > $file.sha256sum.txt
  done
}

function detectJdks() {
  jvm_dir="/usr/lib/jvm/"
  find ${jvm_dir} -maxdepth 1 | sort
  echo "Available jdks 8 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8
  echo "Available jdks 11 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11-     -e jdk-11
  jdk08=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8   | head -n 1))
  jdk11=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11-     -e jdk-11  | head -n 1))
}

function setJdks() {
  set -x
  sed "s|jdk7.home=.*|jdk7.home=$jdk08|g" -i build/build.properties
  sed "s|jdk8.home=.*|jdk8.home=$jdk08|g" -i build/build.properties
  sed "s|jdk9.home=.*|jdk9.home=$jdk11|g" -i build/build.properties
  sed "s|<property name=\"javac.jt.level.bin\" value=\"1.6\" />|<property name=\"javac.jt.level.bin\" value=\"1.7\" />|g" -i build/build.xml
  sed "s|<property name=\"javac.jt.level.src\" value=\"1.6\" />|<property name=\"javac.jt.level.src\" value=\"1.7\" />|g" -i build/build.xml
  set +x
}

function resetRepo() {
  local branch=${1}
  local forceJdk=${2}
  rm -f .git/index.lock ;
  git reset --hard ;
  git checkout $branch
  if [ "x$forceJdk" == "xtrue" ] ; then
    setJdks
  fi
}

function cleanRepo() {
  pushd build
    ant clean
  popd
  rm -rf ../$BUILD_PATH
}

REPO_DIR="sigtest"
BUILD_PATH=SIGTEST_BUILD/
main_file=sigtest
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
else
  rm -vf $REPO_DIR/$main_file*.zip
fi

detectJdks

pushd $REPO_DIR
  resetRepo master false
  rm -rf ../$BUILD_PATH
  tip=`git log | head -n 1 | sed "s/.*\s\+//"` || true
  tip_shortened=`echo ${tip:0:10}`
  latestRelease=`git tag -l | sort -Vr | head -n 1`
  latestReleaseNumber=`echo $latestRelease | sed s/$main_file//g`
  rc=$main_file-$latestRelease

  # latest released
  resetRepo "$latestRelease" true
  pushd build
    ant test | tee ../$rc.zip.txt || true
    ant build
  popd
  mv  ../$BUILD_PATH/$main_file-$latestReleaseNumber.zip $rc.zip
  mv  ../$BUILD_PATH/$main_file-examples-$latestReleaseNumber.zip $rc-exmaples.zip
  echo "Manually renaming $rc.zip as $main_file.zip to provide latest-stable-recommended file"
  ln -sfv $rc.zip $main_file.zip
  cleanRepo

  # tip
  resetRepo master true
  pushd build
    ant test | tee ../$main_file-$tip_shortened.zip.txt || true
    ant build
  popd
  mv  ../$BUILD_PATH/$main_file-$latestReleaseNumber.zip $main_file-$tip_shortened.zip
  mv  ../$BUILD_PATH/$main_file-examples-$latestReleaseNumber.zip $main_file-$tip_shortened-examples.zip
  echo "Manually renaming $main_file-$tip_shortened.zip as $main_file-tip.zip to provide latest-unstable-recommended file"
  ln -sfv $main_file-$tip_shortened.zip $main_file-tip.zip
  cleanRepo

  echo "Resetting repo back to master"
  resetRepo master false
  hashArtifacts
popd
