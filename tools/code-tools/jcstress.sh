#!/bin/bash

#####################################################################
# Script to build jcstress to be reused by jdk testing community    #
# currently builds tip and latest released version                  #
# It also build two snapshots, which are reused by adoptium testing #
#####################################################################

# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function hashArtifacts() {
  echo "Creating checksums all jcstress*.jar"
  for file in `ls jcstress*.jar` ; do
    sha256sum $file > $file.sha256sum.txt
  done
}

function detectJdks() {
  jvm_dir="/usr/lib/jvm/"
  find ${jvm_dir} -maxdepth 1 | sort
  echo "Available jdks 11 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11
  echo "Available jdks 17 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17- -e jdk-17
  jdk11=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11 | head -n 1))
  jdk17=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17- -e jdk-17 | head -n 1))
}

REPO_DIR="jcstress"
main_name=$REPO_DIR
main_file=$main_name.jar
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
else
  rm -vf $REPO_DIR/$main_name*.jar $REPO_DIR/$main_name*.sha256sum.txt
fi
detectJdks
pushd $REPO_DIR
  git checkout master
  tip=`git log | head -n 1 | sed "s/.*\s\+//"` || true
  tip_shortened=`echo ${tip:0:7}`
  latestRelease=`git tag -l | sort -Vr | head -n 1`
  rc=$main_name-$latestRelease.jar

  # latest released
  git checkout $latestRelease
  export JAVA_HOME=$jdk11
  mvn clean install
  mv tests-all/target/$main_name.jar $rc
  echo "Manually renaming $rc as $main_file to provide latest-stable-recommended file"
  ln -fv $rc $main_file
  mvn clean

  # tip
  git checkout master
  export JAVA_HOME=$jdk17
  mvn clean install
  mv tests-all/target/$main_file $main_name-$tip_shortened.jar
  echo "Manually renaming $main_name-$tip_shortened.jar as $main_name-tip.jar to provide latest-unstable-recommended file"
  ln -fv $main_name-$tip_shortened.jar $main_name-tip.jar
  mvn clean

  # 20240222
  git checkout c565311051494f4b9f78ec86eac6282f1de977e2
  export JAVA_HOME=$jdk11
  mvn clean install
  mv -v tests-all/target/$main_name.jar jcstress-tests-all-20240222.jar
  mvn clean

  # 20220908
  git checkout d118775943666d46ca48a50f21b4e07b9ec1f7ed
  export JAVA_HOME=$jdk11
  mvn clean install
  mv -v tests-all/target/$main_name.jar jcstress-tests-all-20220908.jar
  mvn clean

  echo "Resetting repo back to master"
  git checkout master
  hashArtifacts
popd
