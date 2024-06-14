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
  echo "Available jdks 8 in ${jvm_dir}:"
  echo "  Note, that you need at at least jdk 11 to build 20240222, tip and 0.16. Listing all jdks anyway."
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0- -e jdk-1.8.0 -e java-8- -e jdk-8
  echo "Available jdks 11 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11
  echo "Available jdks 17 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17- -e jdk-17
  jdk8=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0- -e jdk-1.8.0 -e java-8- -e jdk-8 | head -n 1))
  jdk11=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11- -e jdk-11 | head -n 1))
  jdk17=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17- -e jdk-17 | head -n 1))
}

function buildJcstress {
  local jdk="${1}"
  local checkout="${2}"
  local target="${3}"
  export JAVA_HOME="${jdk}"
  mvn clean
  git checkout "${checkout}"
  mvn clean install $TESTS
  mv -v "tests-all/target/${main_name}.jar" "${target}"
  mvn clean
  git checkout master
  unset JAVA_HOME
}

TESTS=
#TESTS=-DskipTests # comment me out!
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
  buildJcstress "${jdk11}" "${latestRelease}" "${rc}"
  echo "Manually renaming $rc as $main_file to provide latest-stable-recommended file"
  ln -fv $rc $main_file

  # tip
  buildJcstress "${jdk11}" "master" "${main_name}-${tip_shortened}.jar"
  echo "Manually renaming $main_name-$tip_shortened.jar as $main_name-tip.jar to provide latest-unstable-recommended file"
  ln -fv $main_name-$tip_shortened.jar $main_name-tip.jar

  # 20240222
  buildJcstress "${jdk11}" "c565311051494f4b9f78ec86eac6282f1de977e2" "jcstress-20240222.jar"

  hashArtifacts
popd
