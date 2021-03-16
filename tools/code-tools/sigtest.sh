#!/bin/bash
# shellcheck disable=SC2035

set -eu
export BUILD_DIR

downloadJDK() {
  cd "$BUILD_DIR/"
  JDK_NAME="$1"
  JDK_INSTALLER_FILENAME="$2"
  JDK_TARGET_FOLDER="${BUILD_DIR}/.."
  export JDK_HOME_DIR="${JDK_TARGET_FOLDER}/$3"

  if [[ -s "${JDK_TARGET_FOLDER}/${JDK_INSTALLER_FILENAME}" ]]; then
    echo "${JDK_NAME} binary installer: ${JDK_INSTALLER_FILENAME}, already exists, reusing it"
  else
  	wget --quiet "https://ci.adoptopenjdk.net/userContent/jdk-binaries/${JDK_INSTALLER_FILENAME}" -P "${JDK_TARGET_FOLDER}"
  fi

  if [[ -e "${JDK_HOME_DIR}" ]]; then
  	echo "${JDK_TARGET_FOLDER}/${JDK_INSTALLER_FILENAME} already unpacked into ${JDK_HOME_DIR}, reusing the unpacked JDK"
  else
    echo "Unpacking ${JDK_INSTALLER_FILENAME}"
    unzip "${JDK_TARGET_FOLDER}/${JDK_INSTALLER_FILENAME}" -d "${JDK_TARGET_FOLDER}"
    echo "Waiting for unpacking to finish."
  fi

  if [[ -e "${JDK_HOME_DIR}" ]]; then
    "${JDK_HOME_DIR}"/bin/java -version
  	echo "${JDK_NAME} is available at ${JDK_HOME_DIR}"
  else
  	echo "For some reason, ${JDK_NAME} is NOT available at ${JDK_HOME_DIR}, check if ${JDK_INSTALLER_FILENAME} ran properly."
  fi
  "${JDK_HOME_DIR}"/bin/java -version
}

buildSigTest()
{
  cd "$BUILD_DIR/build"

  echo "Building sigtest"
  set -x
  ant build -Djdk5.home="${JAVA5_HOME}" -Djdk6.home="${JAVA6_HOME}" -Djdk7.home="${JAVA7_HOME}" -Djdk8.home="${JAVA8_HOME}" -Djdk9.home="${JAVA9_HOME}"
  set +x
  cd ../..

  artifact=sigtest

  cd SIGTEST_BUILD

  echo "*** Tar-ing ${artifact} into ${artifact}.tar"
  tar fcv ${artifact}.tar ${artifact}*.zip

  ls ${artifact}*.zip

  echo "*** Moving ${artifact}.tar to .."
  mv $artifact.tar ..

  cd ..
  pwd
  ls *.tar

  echo "*** Gzipping ${artifact}.tar"
  gzip -9 -f ${artifact}.tar
  ls *.tar.gz

  echo "*** Moving ${artifact}.tar.gz to $BUILD_DIR"
  df -k .
  df -k "$BUILD_DIR"
  ls -l ${artifact}.tar.gz
  pwd
  echo mv ${artifact}.tar.gz "$BUILD_DIR"
  mv ${artifact}.tar.gz "$BUILD_DIR"
  echo Cabbage

  cd "$BUILD_DIR"
  ls *.tar.gz
}

cloneSigTest() {
  tagName=$(git describe --tags `git rev-list --tags --max-count=1`)
  echo "Tag: ${tagName}"

  git checkout ${tagName}
}

export JDK9_FOLDER_NAME=jdk-9

cd sigtest

BUILD_DIR=$(pwd)

cloneSigTest

downloadJDK "JDK 5" jdk1.5.0_22.zip jdk1.5.0_22
JAVA5_HOME=${JDK_HOME_DIR}

downloadJDK "JDK 6" JDK6_u45.zip JDK6_u45
JAVA6_HOME=${JDK_HOME_DIR}

downloadJDK "JDK 7" JDK7_u80.zip JDK7_u80
JAVA7_HOME=${JDK_HOME_DIR}

downloadJDK "JDK 8" JDK8_u172.zip JDK8_u172
JAVA8_HOME=${JDK_HOME_DIR}

downloadJDK "JDK 9" JDK9.0.1.zip JDK9.0.1
JAVA9_HOME=${JDK_HOME_DIR}

buildSigTest
