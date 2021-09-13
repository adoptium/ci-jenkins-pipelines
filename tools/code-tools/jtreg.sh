#!/bin/bash
# shellcheck disable=SC2035,SC2116

set -eu

readonly JTREG_5='jtreg5.1-b01'
readonly JTREG_6='jtreg-6+1'

export WORKSPACE="$WORKSPACE/jtreg"
cd "$WORKSPACE"

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

  echo 'Deletion previous build dist...'
  rm -rf build dist
}

buildJTReg()
{ 
  version="jtregtip"
  if [ "$#" -eq 1 ]; then
    version=$1
    if [ "$1" == "jtreg5.1-b01" ]; then
      export BUILD_NUMBER="b01"
      export BUILD_VERSION="5.1"
    fi
    git checkout $version
  else
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
    if [ "$version" == "jtreg5.1-b01" ]; then
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

createChecksum() {
    ARCHIVE_FULL_PATH=$1
    ARCHIVE_NAME=$(basename "${ARCHIVE_FULL_PATH}")
    DESTINATION=$2

    echo "Creating checksum for ${ARCHIVE_FULL_PATH} at ${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"

    sha256sum "${ARCHIVE_FULL_PATH}" > "${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"
}

checkWorkspaceVar
clearWorkspace
echo 'Starting build process...'
buildJTReg "$JTREG_5"
buildJTReg "$JTREG_6"
buildJTReg
echo '...finished with build process.'
