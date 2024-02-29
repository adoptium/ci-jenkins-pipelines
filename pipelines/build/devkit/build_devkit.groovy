package common
/*
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

def build_devkit() {
    def openjdkRepo = "https://github.com/openjdk/${params.VERSION}.git"
 
    checkout scm
 
    sh(script:"git clone ${openjdkRepo} ${params.VERSION}")

    sh(script:"cp pipelines/build/devkit/binutils-2.39.patch ${params.VERSION}/make/devkit/patches/${params.ARCH}-binutils-2.39.patch")

    sh(script:"cd ${params.VERSION} && patch -p1<../pipelines/build/devkit/Tools.gmk.patch")

    sh(script:"make TARGETS=${params.ARCH}-linux-gnu BASE_OS=${params.BASE_OS} BASE_OS_VERSION=${params.BASE_OS_VERSION}")
}

node(params.DEVKIT_BUILD_NODE) {
  try {
    cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

    docker.image(params.DOCKER_IMAGE).pull()

    docker.image(params.DOCKER_IMAGE).inside() {
        build_devkit()

        // Compress and archive
        sh(script:"tar -cf - ${params.VERSION}/build/devkit/result/ | GZIP=-9 gzip -c > ${params.ARCH}-linux-gnu.tar.gz")
        archiveArtifacts artifacts: 'workspace/target/*'
    }
  } finally { 
    cleanWs notFailBuild: true
  } 
}

