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
  jdk17=$(readlink -f $(find ${jvm_dir} -maxdepth 1 | sort | grep -e java-17-     -e jdk-17  | head -n 1))
}

function resetRepo() {
  local branch=${1}
  rm -f .git/index.lock ;
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
  asm_version=$1
  tools="asm asm-tree asm-util"
  main_url="https://repository.ow2.org/nexus/content/repositories/releases/org/ow2/asm"
  ASM_TITLE="Built against '$tools' tools in version '$asm_version'"
  ASM_URLS=""
  ASM_JARS=""
  ASM_PROPS=""
  for tool in $tools; do
    tool_prop="`echo $tool|sed "s/-/./g"`.jar"
    tool_versioned="$tool-$asm_version.jar"
    tool_url="$main_url/$tool/$asm_version/$tool_versioned"
    if [ ! -e $tool_versioned ] ; then
      wget $tool_url
    fi
    ASM_URLS="$ASM_URLS$tool_url
" #ne per line
    ASM_PROPS="$ASM_PROPS -D$tool_prop=`pwd`/$tool_versioned"
    ASM_JARS="$ASM_JARS$tool_versioned:"
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
  echo "You can get one at adoptium: https://ci.adoptium.net/view/Dependencies/job/dependency_pipeline/" #TODO add better link?
  echo "Or build one from: https://github.com/openjdk/jtharness/"
}

function getJavatest() {
  local javatest=javatest.jar
  pushd build
    if [ ! -e $javatest ] ; then
      # For local usage you can build local javatest.jar by running javatest.sh
      # Then also the jtobserver.jar will be built
      # FIXME replace by wget of latest javatest.jar downloaded from published javatest.sh artifacts
      if [ -e  ../../jtharness/$javatest ] ; then
        cp ../../jtharness/$javatest .
      else
        # unsetting the javatestjar will exclude build of 
        sed "s/javatestjar.*/javatestjar=/g" -i build.properties
      fi
   fi
   if [ ! -e testng.jar ] ; then
     local testngv=6.9.10
     wget https://repo1.maven.org/maven2/org/testng/testng/$testngv/testng-$testngv.jar
     mv testng-$testngv.jar testng.jar
   fi
   if [ ! -e jcommander.jar ] ; then
     jcommanderv=1.81
     wget https://repo1.maven.org/maven2/com/beust/jcommander/$jcommanderv/jcommander-$jcommanderv.jar
     mv jcommander-$jcommanderv.jar jcommander.jar
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
  getAsmDeps "9.0"
  getJavatest
  pushd build
    ant $ASM_PROPS test | tee ../$main_file-$tip_shortened.tar.gz.txt || true
    ant $ASM_PROPS build
  popd
  pushd $BUILD_PATH/jcov*/
    getReadme > readme.txt
    tar -czf ../../$main_file-$tip_shortened.tar.gz *.jar readme.txt
  popd
  echo "Manually renaming $main_file-$tip_shortened.tar.gz as $main_file-tip..tar.gz to provide latest-unstable-recommended file"
  ln -fv $main_file-$tip_shortened.tar.gz $main_file-tip.tar.gz
  cleanRepo

  echo "Resetting repo back to master"
  resetRepo master
  hashArtifacts
popd
