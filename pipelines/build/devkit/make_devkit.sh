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

openjdkRepo="https://github.com/openjdk/${VERSION}.git"

# Clone upstream openjdk repo
git clone --depth 1 ${openjdkRepo} ${VERSION}
cd ${VERSION}

# Patch to support Centos7
cp ../binutils-2.39.patch make/devkit/patches/${ARCH}-binutils-2.39.patch
patch -p1 < ../Tools.gmk.patch

devkit_target="${ARCH}-linux-gnu"

# Perform devkit build
cd make/devkit && make TARGETS=${devkit_target} BASE_OS=${BASE_OS} BASE_OS_VERSION=${BASE_OS_VERSION}

# Back to original folder
cd ../../..

echo "DevKit build successful: ${VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target}"

