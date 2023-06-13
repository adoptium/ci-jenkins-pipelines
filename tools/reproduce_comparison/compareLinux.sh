#!/bin/sh
[ $# -lt 1 ] && echo Usage: $0 SBOMURL && exit 1
SBOMURL=$1
ANT_VERSION=1.10.5

installPrereqs() {
  if test -r /etc/redhat-release; then
    yum install -y gcc gcc-c++ make autoconf unzip zip alsa-lib-devel cups-devel   libXtst-devel libXt-devel libXrender-devel libXrandr-devel libXi-devel
    yum install -y file fontconfig fontconfig-devel systemtap-sdt-devel # Not included above ...
    yum install -y git bzip2 xz openssl pigz which # pigz/which not strictly needed but help in final compression
    if grep elease.6 /etc/redhat-release; then
      if [ ! -r /usr/local/bin/autoconf ]; then
        curl https://ftp.gnu.org/gnu/autoconf/autoconf-2.69.tar.gz | tar xpfz - || exit 1
        (cd autoconf-2.69 && ./configure --prefix=/usr/local && make install)
      fi
    fi
  fi
}

# ant required for --create-sbom
function downloadAnt() {
  if [ ! -r /usr/local/apache-ant-${ANT_VERSION}/bin/ant ]; then
    echo Downloading ant for SBOM creation:
    curl https://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.zip > /tmp/apache-ant-${ANT_VERSION}-bin.zip
    (cd /usr/local && unzip -qn /tmp/apache-ant-${ANT_VERSION}-bin.zip)
    rm /tmp/apache-ant-${ANT_VERSION}-bin.zip
  fi
}

installPrereqs
downloadAnt

echo Retrieving and parsing SBOM from "$SBOMURL"
curl -LO $SBOMURL
SBOM=`basename $SBOMURL`
BOOTJDK_VERSION=$(grep configure_arguments $SBOM | tr ' ' \\n | grep ^Temurin- | uniq | cut -d- -f2)
GCCVERSION=$(cat $SBOM | tr ' ' \\n | grep CC= | cut -d- -f2 | cut -d\\ -f1)
LOCALGCCDIR=/usr/local/gcc$(echo $GCCVERSION | cut -d. -f1)
TEMURIN_BUILD_SHA=$(awk -F'"' '/buildRef/{print$4}' $SBOM  | cut -d/ -f7)
TEMURIN_BUILD_ARGS=$(grep makejdk_any_platform_args $SBOM | cut -d\" -f4 | sed -e "s/--disable-warnings-as-errors --enable-dtrace --without-version-pre --without-version-opt/'--disable-warnings-as-errors --enable-dtrace --without-version-pre --without-version-opt'/" -e "s/ --disable-warnings-as-errors --enable-dtrace/ '--disable-warnings-as-errors --enable-dtrace'/" -e 's/\\n//g' -e "s,--jdk-boot-dir [^ ]*,--jdk-boot-dir /usr/lib/jvm/jdk-$BOOTJDK_VERSION,g")
TEMURIN_VERSION=$(awk -F'"' '/semver/{print$4}' $SBOM)

NATIVE_API_ARCH=$(uname -m)
if [ "${NATIVE_API_ARCH}" = "x86_64" ]; then NATIVE_API_ARCH=x64; fi
if [ "${NATIVE_API_ARCH}" = "armv7l" ]; then NATIVE_API_ARCH=arm; fi

[ -z "$SBOM" -o -z "${BOOTJDK_VERSION}" -o -z "${TEMURIN_BUILD_SHA}" -o -z "${TEMURIN_BUILD_ARGS}" -o -z "${TEMURIN_VERSION}" ] && echo Could not determine one of the variables - run with sh -x to diagnose && sleep 10 && exit 1
if [ ! -r /usr/lib/jvm/jdk-${BOOTJDK_VERSION}/bin/javac ]; then
 echo Retrieving boot JDK $BOOTJDK_VERSION && mkdir -p /usr/lib/jvm && curl -L "https://api.adoptopenjdk.net/v3/binary/version/jdk-${BOOTJDK_VERSION}/linux/${NATIVE_API_ARCH}/jdk/hotspot/normal/adoptopenjdk?project=jdk" | (cd /usr/lib/jvm && tar xpzf -)
fi
if [ ! -r ${LOCALGCCDIR}/bin/g++-${GCCVERSION} ]; then
  echo Retrieving gcc $GCCVERSION && curl -L https://ci.adoptium.net/userContent/gcc/gcc$(echo $GCCVERSION | tr -d .).`uname -m`.tar.xz | (cd /usr/local && tar xJpf -) || exit 1
fi
if [ ! -r temurin-build ]; then
  git clone https://github.com/adoptium/temurin-build || exit 1
fi
(cd temurin-build && git checkout $TEMURIN_BUILD_SHA)
export CC="${LOCALGCCDIR}/bin/gcc-${GCCVERSION}"
export CXX="${LOCALGCCDIR}/bin/g++-${GCCVERSION}"
# /usr/local/bin required to pick up the new autoconf if required
export PATH="${LOCALGCCDIR}/bin:/usr/local/bin:/usr/bin:$PATH:/usr/local/apache-ant-${ANT_VERSION}/bin"
ls -ld $CC $CXX /usr/lib/jvm/jdk-${BOOTJDK_VERSION}/bin/javac || exit 1

TARBALL_URL="https://api.adoptium.net/v3/binary/version/jdk-${TEMURIN_VERSION}/linux/${NATIVE_API_ARCH}/jdk/hotspot/normal/eclipse?project=jdk"
if [ ! -d jdk-${TEMURIN_VERSION} ]; then
   echo Retrieving original tarball from adoptium.net && curl -L "$TARBALL_URL" | tar xpfz - && ls -lart $PWD/jdk-${TEMURIN_VERSION} || exit 1
fi
echo "  cd temurin-build && ./makejdk-any-platform.sh $TEMURIN_BUILD_ARGS 2>&1 | tee build.$$.log" | sh

echo Comparing ...
mkdir compare.$$
tar xpfz temurin-build/workspace/target/OpenJDK*-jdk_*tar.gz -C compare.$$
diff -r jdk-${TEMURIN_VERSION} compare.$$/jdk-$TEMURIN_VERSION 2>&1 | tee reprotest.`uname`.$TEMURIN_VERSION.diff
