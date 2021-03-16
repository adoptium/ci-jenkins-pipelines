#!/bin/bash
# shellcheck disable=SC2002,SC2035

set -eu

echo "WORKSPACE: $WORKSPACE"

cd jcov

tagName=$(git describe --tags "$(git rev-list --tags --max-count=1)")
echo "Tag: ${tagName}"

git checkout "${tagName}"

fileName=build/_release.properties;

cat build/release.properties | sed 's/\d./d_/g' > $fileName

buildVersion=$(cat "$fileName" | grep build_version |  cut -d'=' -f2 | cut -d' ' -f2 | tr -d '\r')
buildNumber=$(cat "$fileName" | grep build_number |  cut -d'=' -f2 | cut -d' ' -f2 | tr -d '\r')
buildMilestone=$(cat "$fileName" | grep build_milestone |  cut -d'=' -f2 | cut -d' ' -f2 | cut -d'.' -f2 | tr -d '\r')

if [ ! -f "${WORKSPACE}/jtharness/lib/javatest.jar" ]; then
   wget http://download.java.net/jtharness/4.4.1/Rel/jtharness-4_4_1-MR1-bin-b13-20_dec_2011.zip
   mkdir -p jtharness
   cd jtharness
   unzip -o ../jtharness-4_4_1-MR1-bin-b13-20_dec_2011.zip
   cd ..
fi

if [ ! -d asm-7.0 ]; then
   mkdir asm-7.0
   cd asm-7.0
   wget https://repository.ow2.org/nexus/content/repositories/releases/org/ow2/asm/asm/7.0-beta/asm-7.0-beta.jar
   wget https://repository.ow2.org/nexus/content/repositories/releases/org/ow2/asm/asm-tree/7.0-beta/asm-tree-7.0-beta.jar
   wget https://repository.ow2.org/nexus/content/repositories/releases/org/ow2/asm/asm-util/7.0-beta/asm-util-7.0-beta.jar
   cd ..
fi

ls -lash

cd build

echo "${buildVersion} ${buildNumber} ${buildMilestone}"

ant clean
ant -v build -f build.xml -Dasm.jar="${WORKSPACE}/jcov/asm-7.0/asm-7.0-beta.jar" -Dasm.tree.jar="${WORKSPACE}/jcov/asm-7.0/asm-tree-7.0-beta.jar" -Dasm.util.jar="${WORKSPACE}/jcov/asm-7.0/asm-util-7.0-beta.jar" -Djavatestjar="${WORKSPACE}/jcov/jtharness/lib/javatest.jar"
cd ..

rm -f *.zip
rm -f *.tar.gz

pwd

ls -lash

artifact=jcov-${buildVersion}-${buildMilestone}-${buildNumber}

rm -fr JCOV_BUILD/temp

tar fcv "$artifact.tar" JCOV_BUILD
gzip -9 "${artifact}.tar"

echo "Creating checksum for artifact ${artifact}"
sha256sum "${artifact}.tar.gz" > "${artifact}.tar.gz.sha256sum.txt"

echo "Finished creating artifact: ${artifact}"
