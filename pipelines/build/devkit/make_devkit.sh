#!/bin/bash
################################################################################
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

set -e

if [ $# -lt 4 ]; then
  echo "Usage: $0 VERSION ARCH BASE_OS BASE_OS_VERSION"
  echo "eg: $0 jdk21u aarch64 Centos 7.6.1810"
  exit 1
fi

VERSION=$1
ARCH=$2
BASE_OS=$3
BASE_OS_VERSION=$4

# Create temp GPG home
GNUPGHOME="$(mktemp -d /tmp/.gpg-temp.XXXXXX)"
chmod 700 ${GNUPGHOME}
export GNUPGHOME

openjdkRepo="https://github.com/openjdk/${VERSION}.git"

# Clone upstream openjdk repo
git clone --depth 1 ${openjdkRepo} ${VERSION}
cd ${VERSION}

# Patch to support Centos7
cp ../binutils-2.39.patch make/devkit/patches/${ARCH}-binutils-2.39.patch
patch -p1 < ../Tools.gmk.patch

devkit_target="${ARCH}-linux-gnu"

if [ "${BASE_OS}" = "rhel" ]; then
  mkdir -p ../../../build/devkit/${VERSION}/build/devkit/download/rpms/s390x-linux-gnu-Centos${BASE_OS_VERSION}
  # Downlod RPMS from RHEL (Requires machine to be attached to RHEL subscription)
  RPMDIR=/var/cache/yum/s390x/7Server/rhel-7-for-system-z-rpms/packages
  pwd
  for A in glibc glibc-headers glibc-devel cups-libs cups-devel libX11 libX11-devel xorg-x11-proto-devel alsa-lib alsa-lib-devel libXext libXext-devel libXtst libXtst-devel libXrender libXrender-devel libXrandr libXrandr-devel freetype freetype-devel libXt libXt-devel libSM libSM-devel libICE libICE-devel libXi libXi-devel libXdmcp libXdmcp-devel libXau libXau-devel libgcc libxcrypt zlib zlib-devel libffi libffi-devel fontconfig fontconfig-devel kernel-headers; do
    if [ ! -z "$(ls $RPMDIR/${A}-[0-9]*${ARCH}*.rpm)" ]; then
      cp -pv ${RPMDIR}/${A}-[0-9]*${ARCH}*.rpm "../../../build/devkit/${VERSION}/build/devkit/download/rpms/s390x-linux-gnu-Centos${BASE_OS_VERSION}"
    elif [ ! -z "$(ls $RPMDIR/${A}-[0-9]*noarch.rpm)" ]; then
      cp -pv ${RPMDIR}/${A}-[0-9]*noarch.rpm "../../../build/devkit/${VERSION}/build/devkit/download/rpms/s390x-linux-gnu-Centos${BASE_OS_VERSION}"
    fi
  done
  # Temporary fudge to use Centos logic until we adjust Tools.gmk
  BASE_OS=Centos
fi

# Perform "bootstrap" devkit build
echo "Building 'bootstrap' DevKit toolchain, to be used to build the final DevKit..."
cd make/devkit && pwd && make TARGETS=${devkit_target} BASE_OS=${BASE_OS} BASE_OS_VERSION=${BASE_OS_VERSION}

# Move "bootstrap" devkit toolchain to a new folder and setup gcc toolchain to point at it
cd ../..
BOOTSTRAP_DEVKIT="$(pwd)/build/bootstrap_${devkit_target}-to-${devkit_target}"
mv build/devkit/result/${devkit_target}-to-${devkit_target} ${BOOTSTRAP_DEVKIT}

# Make final "DevKit" using the bootstrap devkit
rm -rf build/devkit
echo "Building 'final' DevKit toolchain, using 'bootstrap' toolchain in ${BOOTSTRAP_DEVKIT}"
cd make/devkit && pwd && \
  LD_LIBRARY_PATH="${BOOTSTRAP_DEVKIT}/lib64:${BOOTSTRAP_DEVKIT}/lib" \
  PATH="${BOOTSTRAP_DEVKIT}/bin:$PATH" \
  make TARGETS=${devkit_target} BASE_OS=${BASE_OS} BASE_OS_VERSION=${BASE_OS_VERSION} \
       CC=${BOOTSTRAP_DEVKIT}/bin/gcc \
       CXX=${BOOTSTRAP_DEVKIT}/bin/g++ \
       LD=${BOOTSTRAP_DEVKIT}/bin/ld \
       AR=${BOOTSTRAP_DEVKIT}/bin/ar \
       AS=${BOOTSTRAP_DEVKIT}/bin/as \
       RANLIB=${BOOTSTRAP_DEVKIT}/bin/ranlib \
       OBJDUMP=${BOOTSTRAP_DEVKIT}/bin/objdump

# Back to original folder
cd ../../..

echo "DevKit build successful: ${VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target}"

