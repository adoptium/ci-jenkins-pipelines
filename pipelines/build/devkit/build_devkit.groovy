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

/*
 * Builds a linux-gnu DevKit for the given openjdk version and architecture.
 * Supported architectures:
 *   x86_64
 *   aarch64
 *   ppc64le
 *   s390x
 */

def build_devkit() {
    stage('Build DevKit') {
        def openjdkRepo = "https://github.com/openjdk/${params.VERSION}.git"
 
        // Clone upstream openjdk repo
        sh(script:"git clone --depth 1 ${openjdkRepo} ${params.VERSION}")

        // Patch to support Centos7
        sh(script:"cp pipelines/build/devkit/binutils-2.39.patch ${params.VERSION}/make/devkit/patches/${params.ARCH}-binutils-2.39.patch")
        sh(script:"cd ${params.VERSION} && patch -p1<../pipelines/build/devkit/Tools.gmk.patch")

        // Perform devkit build
        sh(script:"cd ${params.VERSION}/make/devkit && echo make TARGETS=${params.ARCH}-linux-gnu BASE_OS=${params.BASE_OS} BASE_OS_VERSION=${params.BASE_OS_VERSION}")
 
        // Compress and archive
        sh(script:"tar -cf - ${params.VERSION}/build/devkit/result/ | GZIP=-9 gzip -c > workspace/${params.ARCH}-linux-gnu.tar.gz")

        // Create sha256.txt
        sh(script:"sha256sum workspace/${params.ARCH}-linux-gnu.tar.gz > workspace/${params.ARCH}-linux-gnu.tar.gz.sha256.txt")

        archiveArtifacts artifacts: "workspace/*"
    }
}

def gpgSign() {
    stage('GPG sign') {
        def params = [
                  string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                  string(name: 'UPSTREAM_JOB_NAME',   value: "${env.JOB_NAME}"),
                  string(name: 'UPSTREAM_DIR',        value: 'workspace')
        ]

        def signSHAsJob = build job: 'build-scripts/release/sign_temurin_gpg',
               propagate: true,
               parameters: params

        copyArtifacts(
               projectName: 'build-scripts/release/sign_temurin_gpg',
               selector: specific("${signSHAsJob.getNumber()}"),
               filter: '**/*.sig',
               fingerprintArtifacts: true,
               target: 'workspace',
               flatten: true)

        archiveArtifacts artifacts: "workspace/*.sig"
    }
}

node(params.DEVKIT_BUILD_NODE) {
  try {
    cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

    docker.image(params.DOCKER_IMAGE).pull()
    docker.image(params.DOCKER_IMAGE).inside() {
        // Checkout pipelines code
        checkout scm

        // Create workspace for artifacts
        sh("mkdir workspace")

        build_devkit()
        gpgSign()
    }
  } finally { 
    cleanWs notFailBuild: true
  } 
}
