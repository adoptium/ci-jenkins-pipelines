#!/bin/bash
# shellcheck disable=SC2035,SC2155
set -eu

function generateArtifact() {
    tag=$1
    version=$2

    echo "tag=$tag"
    echo "version=$version"

	artifact=asmtools-$version

	tagName=$(hg tags | grep "$tag" | head -1 | awk '{ print $1 }')
	echo "Tag: ${tagName}"

	rm -fr asmtools

	hg clone http://hg.openjdk.java.net/code-tools/asmtools -r "${tagName}"

	# In WORKSPACE until here
	echo "Moving into asmtools/build..."
	cd asmtools/build

    perl -p -i -e 's/"9"/"1.8"/g' build.xml

	echo "Building asmtools"
    #export JAVA_HOME=/usr/lib/jvm/jdk-11.0.5+10
    export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

    ant build -Djava.home=/usr/lib/jvm/java-8-openjdk-amd64

	# WORKSPACE/asmtools/build/BUILD_DIR
	echo "Moving down to ${BUILD_DIR}"
	cd "${BUILD_DIR}"

    echo "Asmtools artifact = $artifact"

    echo "Copying release/lib/asmtools*.jar file to ../"
    cp release/lib/asmtools*.jar ..

	# WORKSPACE/asmtools/build/BUILD_DIR/dist
    echo "Moving into $artifact-build/dist"
    cd dist

	echo "tar-ing ${artifact}.zip into ${artifact}.tar"
	tar fcv "$artifact.tar" *.zip

	echo "Moving ${artifact}.tar to ../.."
	mv "$artifact.tar" ../..
	# WORKSPACE/asmtools/build/
	cd ../..

	echo "gzipping ${artifact}.tar"
	gzip -9 -f "${artifact}.tar"

	if [ -d "$artifact-build" ];
	then
		mv "$artifact-build" asmtools/
	fi

    echo "Creating checksums for asmtools.jar and ${artifact}.tar.gz"
    sha256sum asmtools.jar > asmtools.jar.sha256sum.txt
    sha256sum "${artifact}.tar.gz" > "${artifact}.tar.gz.sha256sum.txt"

    echo "Moving into asmtools"
	cd asmtools
}

export PRODUCT_VERSION=$(grep "PRODUCT_VERSION     \= " build/productinfo.properties | awk '{print $3}')
# shellcheck disable=SC2005,SC2046
export BUILD_DIR=$(echo $(eval echo $(grep "BUILD_DIR = " build/build.properties | awk '{print $3}')))

generateArtifact "tip" "${PRODUCT_VERSION}"


mv *.jar.sha256sum.txt asmtools
mv *.tar.gz.sha256sum.txt asmtools
mv *.jar asmtools
mv *.tar.gz asmtools
