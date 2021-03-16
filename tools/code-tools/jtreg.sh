#!/bin/bash
# shellcheck disable=SC2035,SC2116

set -eu

readonly TIP_VERSION='4.2.0'
readonly TESTNG_VERSION='6.9.5'
readonly JUNIT_VERSION='4.10'
readonly JCOMMANDER_VERSION='1.48'
export ASMTOOLS_HOME=""

export WORKSPACE="$WORKSPACE/jtreg"
cd "$WORKSPACE"
export JCOV_HOME
export JTHARNESS_HOME

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

function downloadJavaHelp()
{
  echo -n 'Checking for downloaded javahelp...'
  if [ -d 'jh2.0' ] ; then
    echo ' found.'
  else
    echo ' not found, downloading:'
    rm -f javahelp2_0_05.zip
    # wget http://download.java.net/javadesktop/javahelp/javahelp2_0_05.zip
    wget https://github.com/glub/secureftp/raw/master/contrib/javahelp2_0_05.zip
    unzip -o javahelp2_0_05.zip
  fi
}

function downloadJUnit()
{
  echo -n 'Checking for downloaded junit...'
  if [ -s 'junit/junit.jar' ] && [ -s 'junit/'${JUNIT_VERSION} ]; then
    echo ' found.'
  else
    echo ' not found, downloading:'
    # shellcheck disable=SC2035
    ( mkdir -p 'junit' && cd 'junit' && rm -rf * && wget http://repo1.maven.org/maven2/junit/junit/${JUNIT_VERSION}/junit-${JUNIT_VERSION}.jar && mv junit-${JUNIT_VERSION}.jar junit.jar && touch ${JUNIT_VERSION})
  fi
}

function downloadTestNG()
{
  echo -n 'Checking for downloaded testng...'
  if [ -s 'testng/testng.jar' ] && [ -s 'testng/'${TESTNG_VERSION} ]; then
    echo ' found.'
  else
    echo ' not found, downloading:'
    ( rm -rf testng* && wget http://jcenter.bintray.com/org/testng/testng/$TESTNG_VERSION/testng-$TESTNG_VERSION.jar && mkdir -p testng && mv testng-$TESTNG_VERSION.jar testng/testng.jar && touch ${TESTNG_VERSION})
  fi
}

function downloadJCov()
{
  JCOV_SHORT_VERSION="3.0"
  JCOV_FULL_VERSION="jcov-${JCOV_SHORT_VERSION}-beta-2"
  tar -zxvf ${JCOV_FULL_VERSION}.tar.gz
  mv JCOV_BUILD jcov
  mv jcov/jcov_${JCOV_SHORT_VERSION} jcov/lib
  JCOV_HOME="$( cd jcov && pwd )"
}

function downloadAsmTools()
{
  TGZ_EXTENSION="tar.gz"
  ASMTOOLS_ARTIFACT=$(ls asmtools*.tar.gz)
  ASMTOOLS_ARTIFACT=$(echo "${ASMTOOLS_ARTIFACT%.*}")
  ASMTOOLS_ARTIFACT=$(echo "${ASMTOOLS_ARTIFACT%.*}")
  tar -xzvf "${ASMTOOLS_ARTIFACT}.${TGZ_EXTENSION}"
  ## tar contains zip for some reason:
  unzip -o "${ASMTOOLS_ARTIFACT}.zip"
  mv "${ASMTOOLS_ARTIFACT}" asmtools
  ASMTOOLS_HOME="$( cd asmtools && pwd )"
}

function downloadJTHarness()
{
  export JTHARNESS=jtharness
  tar -zxvf ${JTHARNESS}.tar.gz
  JTHARNESS_HOME="$( cd jtharness && pwd )"
}

function downloadJCommander()
{
  echo -n 'Checking for downloaded jcommander...'
  if [ -s 'jcommander/jcommander.jar' ] [ -s 'jcommander/'${JCOMMANDER_VERSION} ]; then
    echo ' found.'
  else
    echo ' not found, downloading:'
    ( rm -rf 'jcommander' && mkdir 'jcommander' && cd 'jcommander' && wget http://repo1.maven.org/maven2/com/beust/jcommander/${JCOMMANDER_VERSION}/jcommander-${JCOMMANDER_VERSION}.jar && mv jcommander-${JCOMMANDER_VERSION}.jar jcommander.jar && touch ${JCOMMANDER_VERSION})
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
  echo ""
  echo "***********************************************"
  echo "Building $1..."
  echo "***********************************************"
  (
   versionNumber=$2
   buildNumber=$3
   echo "VersionNumber: ${versionNumber}"
   echo "BuildNumber: ${buildNumber}"
   echo "JAVA_HOME: $JAVA_HOME"

   echo "Changing into $WORKSPACE"
   cd "$WORKSPACE"
   echo "Currently in $PWD"

   echo "Removing contents of build folder"
   rm -fr build || true

#   cd make
   chmod +x make/build-all.sh
   make/build-all.sh "$JAVA_HOME"

   cd build/images

   createWin32FolderWithJTRegBinaries

   tar -cvf jtreg.tar jtreg
   gzip -9 jtreg.tar
   mv jtreg.tar.gz "$WORKSPACE/jtreg-$versionNumber-$buildNumber.tar.gz"
   createChecksum "$WORKSPACE/jtreg-$versionNumber-$buildNumber.tar.gz" "$WORKSPACE"
  )
}

createWin32FolderWithJTRegBinaries()
{
   mkdir -p jtreg/win32
   cp -fr jtreg/bin jtreg/win32/
}

buildJTRegTip()
{
  buildJTReg "the tip" "$TIP_VERSION" "tip"
}

buildJTRegLastTag()
{
  tagName=$(git describe --tags "$(git rev-list --tags --max-count=1)")
    echo "Tag: ${tagName}"
  versionAndBuildNumber=$(echo "${tagName}"| awk '{split($0,a,"jtreg"); print a[2]}')
  versionNumber=$(echo "${versionAndBuildNumber}" | awk '{split($0,a,"-"); print a[1]}')
  buildNumber=$(echo "${versionAndBuildNumber}" | awk '{split($0,a,"-"); print a[2]}')
  git checkout "${tagName}"

  buildJTReg "last tag" "$versionNumber" "$buildNumber"
}

createChecksum() {
    ARCHIVE_FULL_PATH=$1
    ARCHIVE_NAME=$(basename "${ARCHIVE_FULL_PATH}")
    DESTINATION=$2

    echo "Creating checksum for ${ARCHIVE_FULL_PATH} at ${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"

    sha256sum "${ARCHIVE_FULL_PATH}" > "${DESTINATION}/${ARCHIVE_NAME}.sha256sum.txt"
}

runBasicJtregSanityTest() {
    make "$(pwd)"/Basic.othervm.ok "$(pwd)"/Basic.agentvm.ok
}

runJtregSanitySelfTests() {
    make -j 4 test
}

checkWorkspaceVar
clearWorkspace

echo 'Starting build process...'
buildJTRegTip
buildJTRegLastTag
echo '...finished with build process.'
