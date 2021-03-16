#!/bin/bash
# shellcheck disable=SC2035

set -eu

cd jtharness

tagName=$(git describe --tags `git rev-list --tags --max-count=1`)
echo "Tag: ${tagName}"

git checkout ${tagName}

#rm -fr jh2.0
if [ ! -d "jh2.0" ]; then
   wget https://github.com/glub/secureftp/raw/master/contrib/javahelp2_0_05.zip
   unzip -o javahelp2_0_05.zip
   cp jh2.0/javahelp/lib/jhall.jar build/
   cp jh2.0/javahelp/lib/jh.jar build/
fi


#rm -fr junit
if [ ! -d junit ]; then
   mkdir junit
   cd junit

   wget https://repo1.maven.org/maven2/junit/junit/4.4/junit-4.4.jar
   cp junit-4.4.jar ../build/

   cd ..
fi

#rm -fr jcomapi
if [ ! -d jcomapi ]; then
   mkdir jcomapi
   cd jcomapi

   wget www.java2s.com/Code/JarDownload/comm/comm-2.0.jar.zip
   unzip comm-2.0.jar.zip
   cp comm-2.0.jar ../build/comm.jar

   cd ..
fi

#rm -fr servletapi
if [ ! -d servletapi ]; then
   mkdir servletapi
   cd servletapi

   wget www.java2s.com/Code/JarDownload/servlet/servlet-api.jar.zip
   unzip servlet-api.jar.zip
   mv servlet-api.jar ../build/

   cd ..
fi

#rm -fr asm
if [ ! -d asm ]; then
   mkdir asm
   cd asm

   wget www.java2s.com/Code/JarDownload/asm/asm-3.1.jar.zip
   unzip asm-3.1.jar.zip
   mv asm-3.1.jar ../build/

   wget www.java2s.com/Code/JarDownload/asm/asm-commons-3.1.jar.zip
   unzip asm-commons-3.1.jar.zip
   mv asm-commons-3.1.jar ../build/

   cd ..
fi


ls -lash

cd build

rm local.properties

# shellcheck disable=SC2129
echo '#Please specify location of jhall.jar here - for compilation' >> local.properties
echo 'jhalljar = ./build/jhall.jar' >> local.properties
echo '' >> local.properties
echo '# needed only at runtime' >> local.properties
echo 'jhjar = ./build/jh.jar' >> local.properties
echo '' >> local.properties
echo '# location of jar with with implementation of java serial communications API' >> local.properties
echo 'jcommjar = ./build/comm.jar' >> local.properties
echo '' >> local.properties
echo '# location of jar with servlet API implementation' >> local.properties
echo 'servletjar = ./build/servlet-api.jar' >> local.properties
echo '' >> local.properties
echo '# bytecode library (BCEL or ASM)' >> local.properties
echo '# these are not interchangable' >> local.properties
echo 'bytecodelib = ./build/asm-3.1.jar:./build/asm-commons-3.1.jar' >> local.properties
echo '' >> local.properties
echo '# JUnit Library - Version 4 currently used to compile 3 and 4 support' >> local.properties
echo 'junitlib = ./build/junit-4.4.jar' >> local.properties
echo '' >> local.properties
echo '# Please specify location where the build distribution (output) will be created' >> local.properties
echo 'BUILD_DIR = ./JTHarness-build' >> local.properties

pwd

which java
whereis java
java -version
echo "Building jtharness"
ant build -propertyfile ./local.properties -Djvmargs='-Xdoclint:none' -debug
cd ..

rm -f *.zip
rm -f *.tar.gz

artifact='jtharness'


ROOT_FOLDER=$(pwd)
cd JTHarness-build/binaries

echo "$ROOT_FOLDER"

cp -fr "$ROOT_FOLDER/legal/" .
touch COPYRIGHT-javatest.html
mkdir -p doc/javatest
touch doc/javatest/javatestGUI.pdf

pwd
ls -lash
echo "tar-ing jtharness into ${artifact}.tar"
cd ..
mv binaries jtharness
tar fcv "$artifact.tar" jtharness
pwd
echo "moving ${artifact}.tar to .."
mv "$artifact.tar" ..
pwd
cd ..
pwd
echo "gzipping ${artifact}.tar"
gzip -9 "${artifact}.tar"

echo "Creating checksum for ${artifact}.tar.gz"
sha256sum "${artifact}.tar.gz" > "${artifact}.tar.gz.sha256sum.txt"

pwd
rm -fr "${artifact}"
