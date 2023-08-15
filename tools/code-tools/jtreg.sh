#!/bin/bash
# shellcheck disable=SC2035,SC2116

set -eu

readonly JTREG_5='jtreg5.1-b01'
readonly JTREG_6='jtreg-6+1'
readonly JTREG_6_1='jtreg-6.1+1'
readonly JTREG_7='jtreg-7+1'
readonly JTREG_7_1='jtreg-7.1.1+1'
readonly JTREG_7_2='jtreg-7.2+1'
readonly JTREG_7_3='jtreg-7.3+1'

function checkWorkspaceVar()
{
  echo 'Checking WORKSPACE variable...'

  if [ -z "$WORKSPACE" ] ; then
    echo -n 'WORKSPACE variable is empty, setting to current dir: '
    WORKSPACE="$( pwd )"
    echo "$WORKSPACE"
  else
    echo "WORKSPACE variable is already set to '$WORKSPACE'"
  fi
}

function clearWorkspace()
{
  echo 'Cleaning workspace before the build...'
  rm -rf *.zip *.tar.gz

  unset JAVATEST_HOME

  echo 'Deleting previous build dist...'
  rm -rf build dist
}

buildJTReg()
{
  version="jtregtip"
  if [ "$#" -eq 1 ]; then
    version=$1
    if [ "$1" == "$JTREG_5" ]; then
      export BUILD_NUMBER="b01"
      export BUILD_VERSION="5.1"
    elif [ "$1" == "$JTREG_6" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="6"
    elif [ "$1" == "$JTREG_6_1" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="6.1"
    elif [ "$1" == "$JTREG_7" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="7"
      export JAVA_HOME=/usr/lib/jvm/jdk-11
      export PATH=$PATH:$JAVA_HOME/bin
    elif [ "$1" == "$JTREG_7_1" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="7.1.1"
      export JAVA_HOME=/usr/lib/jvm/jdk-11
      export PATH=$PATH:$JAVA_HOME/bin
    elif [ "$1" == "$JTREG_7_2" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="7.2"
      export JAVA_HOME=/usr/lib/jvm/jdk-11
      export PATH=$PATH:$JAVA_HOME/bin
    elif [ "$1" == "$JTREG_7_3" ]; then
      export JTREG_BUILD_NUMBER="1"
      export BUILD_VERSION="7.3"
      export JAVA_HOME=/usr/lib/jvm/jdk-11
      export PATH=$PATH:$JAVA_HOME/bin
    fi
    git checkout $version
  else
    unset BUILD_NUMBER
    unset BUILD_VERSION
    unset JTREG_BUILD_NUMBER
    export JAVA_HOME=/usr/lib/jvm/jdk-11
    export PATH=$PATH:$JAVA_HOME/bin
    git checkout master
  fi

  echo ""
  echo "***********************************************"
  echo "Building JTREG $version..."
  echo "***********************************************"
  (
    echo "JAVA_HOME: $JAVA_HOME"
    echo "Changing into $WORKSPACE"
    cd "$WORKSPACE"
    echo "Currently in $PWD"

    echo "Removing contents of build folder"
    rm -fr build || true
    if [ "$version" == "$JTREG_5" ]; then
      chmod +x make/build-all.sh
      make/build-all.sh "$JAVA_HOME"
    else
      chmod +x make/build.sh
      make/build.sh --jdk "$JAVA_HOME"
    fi

    cd build/images

    createWin32FolderWithJTRegBinaries

    tar -cvf jtreg.tar jtreg
    gzip -9 jtreg.tar
    mv jtreg.tar.gz "$WORKSPACE/$version.tar.gz"
    createChecksum "$WORKSPACE/$version.tar.gz" "$WORKSPACE"
    git reset --hard HEAD
  )
}

createWin32FolderWithJTRegBinaries()
{
  mkdir -p jtreg/win32
  cp -fr jtreg/bin jtreg/win32/
}

createChecksum()
{
  ARCHIVE_FULL_PATH=$1
  ARCHIVE_NAME=$(basename "${ARCHIVE_FULL_PATH}")
  DESTINATION=$2

  echo "Creating checksum for ${ARCHIVE_FULL_PATH} at ${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"

  sha256sum "${ARCHIVE_FULL_PATH}" > "${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"
}

checkWorkspaceVar
clearWorkspace
echo 'Starting build process...'
export WORKSPACE="$WORKSPACE/jtreg"
cd "$WORKSPACE"
buildJTReg "$JTREG_5"
buildJTReg "$JTREG_6"
buildJTReg "$JTREG_6_1"
buildJTReg "$JTREG_6_1"
buildJTReg "$JTREG_7"
buildJTReg "$JTREG_7_1"
buildJTReg "$JTREG_7_2"
buildJTReg "$JTREG_7_3"
buildJTReg
echo '...finished with build process.'
