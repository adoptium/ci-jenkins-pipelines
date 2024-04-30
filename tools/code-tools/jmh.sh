#!/bin/bash

################################################################
# Script to build jmh to be reused by jdk testing community    #
# currently builds tip and latest released version             #
################################################################

# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function hashJars() {
  pushd "${1}"
    for file in `ls jmh*.jar benchmark*.jar` ; do
      sha256sum $file > $file.sha256sum.txt
    done
  popd
}

function hashTars() {
  for file in `ls jmh*.tar.gz` ; do
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

function buildJmh {
  local jdk="${1}"
  local checkout="${2}"
  export JAVA_HOME="${jdk}"
  mvn clean
  local dirName="${main_name}-${checkout}"
  rm -rf "${dirName}"
  mkdir "${dirName}"
  git checkout "${checkout}"
  mvn clean install ${TESTS}
  local jars=$(find -mindepth 2 -type f | grep "\\.jar$" | grep -v "/benchmarks.jar")
  mv -v ${jars} "${dirName}"
  mv -v ./jmh-samples/target/benchmarks.jar "${dirName}/jmh-samples-benchmarks.jar" # avoiding flat clash
  mv -v ./jmh-core-benchmarks/target/benchmarks.jar "${dirName}"
  hashJars "${dirName}"
  tar -czf "${dirName}.tar.gz" "${dirName}"
  rm -rf "${dirName}"
  mvn clean
  git checkout master
  unset JAVA_HOME
}

# tests for jmh are pretty lenghty consider just building 
#TESTS="-DskipTests"
TESTS=""
REPO_DIR="jmh"
main_name=$REPO_DIR
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
else
  rm -vf $REPO_DIR/$main_name*.jar $REPO_DIR/$main_name*.sha256sum.txt $REPO_DIR/$main_name*.tar.gz
fi
detectJdks
pushd $REPO_DIR
  git checkout master
  tip=`git log | head -n 1 | sed "s/.*\s\+//"` || true
  tip_shortened=`echo ${tip:0:7}`
  latestRelease=`git tag -l | sort -Vr | head -n 1`

  # latest released
  buildJmh "${jdk11}" "${latestRelease}"

  # tip
  buildJmh "${jdk17}" "master"

  # version less latest release and version full tip
  cp -v "jmh-${latestRelease}.tar.gz" "jmh.tar.gz"
  cp -v "jmh-master.tar.gz" "jmh-${tip_shortened}.tar.gz"

  hashTars
popd
