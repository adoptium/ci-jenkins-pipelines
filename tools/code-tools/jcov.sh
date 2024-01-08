#!/bin/bash

###################################################################
# Script to build jcov reusable by jdk testing community          #
# It requires asm jars - downloaded during build                  #
# It optionally requires javatest.jar -                           #
#    obtained from older release, buildable on demand or skipped  #
# currently builds tip and latest released version                #
###################################################################

# shellcheck disable=SC2035,SC2155
set -euo pipefail
WORKSPACE=$PWD

function hashArtifacts() {
  echo "Creating checksums all jcov*.tar.gz"
  for file in `ls jcov*.tar.gz` ; do
    sha256sum $file > $file.sha256sum.txt
  done
}

function detectJdks() {
  jvm_dir="/usr/lib/jvm/"
  find ${jvm_dir} -maxdepth 1 | sort
  echo "Available jdks 17 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17-     -e jdk-17
  echo "Available jdks 11 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11-     -e jdk-11
  echo "Available jdks 8 in ${jvm_dir}:"
  find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8
  jdk17=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17-     -e jdk-17  | head -n 1))
  jdk11=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-11-     -e jdk-11  | head -n 1))
  jdk08=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-1.8.0-  -e jdk-8   | head -n 1))
}

function resetRepo() {
  local branch=${1}
  rm -f .git/index.lock ;
  rm -vf *.jar
  rm -vf build/*.jar
  git reset --hard ;
  git checkout $branch
}

function cleanRepo() {
  pushd build
    ant clean
  popd
  rm -rf ../$BUILD_PATH
}

function getAsmDeps() {
  set +u
  local asm_version=$1
  set -u
  local asm_manual="true"
  if [ "x$asm_version" == "x" ] ; then
    asm_version=`cat build/build.properties | grep ^asm.version | sed "s/.*\s*=\s*//g"`
    if [ "x$asm_version" == "x" ] ; then
      echo "no asm-tools version provided and detection (jcov3.0-b08 and up) failed"
      exit 1
    fi
    local asm_manual="false"
  fi
  local tools="asm asm-tree asm-util"
  local main_url="https://repository.ow2.org/nexus/content/repositories/releases/org/ow2/asm"
  ASM_TITLE="Built against '$tools' tools in version '$asm_version'"
  ASM_URLS=""
  ASM_JARS=""
  ASM_PROPS=""
  for tool in $tools; do
    local tool_prop="`echo $tool|sed "s/-/./g"`.jar"
    local tool_versioned="$tool-$asm_version.jar"
    local tool_url="$main_url/$tool/$asm_version/$tool_versioned"
    local tool_checksum_url="$tool_url.md5"
    if [ "$asm_manual" == "true" ] ; then
      if [ ! -e $tool_versioned ] ; then
        wget $tool_checksum_url
        wget $tool_url
        check_md5sum=`md5sum $tool_versioned | cut -d" " -f1`
        check_file=`cat $tool_versioned.md5`
        if [ $check_md5sum == $check_file ] ; then
          echo "Checksums For $tool_versioned Match - OK"
        else
          echo "Error - Checksums For $tool_versioned DO NOT Match"
          exit 1
        fi
      fi
      ASM_URLS="$ASM_URLS$tool_url
" #one per line
      ASM_PROPS="$ASM_PROPS -D$tool_prop=`pwd`/$tool_versioned"
      ASM_JARS="$ASM_JARS$tool_versioned:"
    else
      ASM_URLS="$ASM_URLS$tool_url
" #one per line
      ASM_PROPS="" # it will be set properly from build
      ASM_JARS="$ASM_JARS$tool_versioned:"
    fi
  done
}

function getReadme() {
  echo $ASM_TITLE
  echo ""
  echo "Get:"
  echo "$ASM_URLS"
  echo ""
  echo "Use on CP:"
  echo $ASM_JARS""
  echo ""
  echo "In addition jtobserver.jar requires javatest.jar"
  echo "You can get one at Adoptium: https://ci.adoptium.net/view/Dependencies/job/dependency_pipeline/lastSuccessfulBuild/artifact/jtharness/javatest.jar"
  echo "Or build one from: https://github.com/openjdk/jtharness/"
}

function getJavatest() {
  local javatest=javatest.jar
  pushd build
    if [ ! -e $javatest ] ; then
      # For local usage you can build local javatest.jar by running javatest.sh
      # Then also the jtobserver.jar will be built
      # unsetting the javatestjar will exclude build of jtobserver
      wget https://ci.adoptium.net/view/Dependencies/job/dependency_pipeline/lastSuccessfulBuild/artifact/jtharness/javatest.jar
    fi
  popd
}

REPO_DIR="jcov"
BUILD_PATH=JCOV_BUILD/
main_file=jcov
if [ ! -e $REPO_DIR ] ; then
  git clone https://github.com/openjdk/$REPO_DIR.git
else
  rm -vf $REPO_DIR/$main_file*.tar.gz
fi

detectJdks

pushd $REPO_DIR

  resetRepo master
  rm -rf ../$BUILD_PATH
  tip=`git log | head -n 1 | sed "s/.*\s\+//"` || true
  tip_shortened=`echo ${tip:0:10}`
  latestRelease=`git tag -l  | sed "s/rc/b000/g" | sort -Vr  | sed "s/b000/rc/" | head -n 1`
  rc=$main_file-$latestRelease

  # latest released
  resetRepo "$latestRelease"
  getAsmDeps "8.0.1"
  getJavatest
  pushd build
    export JAVA_HOME="$jdk08"
    ant $ASM_PROPS build
  popd
  pushd $BUILD_PATH/jcov*/
    getReadme > readme.txt
    tar -czf ../../$rc.tar.gz *.jar readme.txt
  popd
  echo "Manually renaming $rc.tar.gz  as $main_file.tar.gz to provide latest-stable-recommended file"
  ln -fv $rc.tar.gz  $main_file.tar.gz
  cleanRepo

  # tip
  resetRepo master
  getAsmDeps
  # pushd build
  #   export JAVA_HOME="$jdk17"
  #   ant $ASM_PROPS test | tee ../$main_file-$tip_shortened.tar.gz.txt || true
  #   ant $ASM_PROPS build
  # popd
  # pushd $BUILD_PATH/jcov*/
  #   getReadme > readme.txt
  #   tar -czf ../../$main_file-$tip_shortened.tar.gz *.jar readme.txt
  # popd
  # echo "Manually renaming $main_file-$tip_shortened.tar.gz as $main_file-tip..tar.gz to provide latest-unstable-recommended file"
  # ln -fv $main_file-$tip_shortened.tar.gz $main_file-tip.tar.gz
  # cleanRepo
  #
  # echo "Resetting repo back to master"
  # resetRepo master
  # hashArtifacts
popd
