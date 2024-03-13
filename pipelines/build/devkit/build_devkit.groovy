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
        // Make DevKit
        sh("cd pipelines/build/devkit && ./make_devkit.sh ${params.VERSION} ${params.ARCH} ${params.BASE_OS} ${params.BASE_OS_VERSION}")

        def devkit_target="${params.ARCH}-linux-gnu"

        // Get gcc version and base OS from devkit.info
        def gcc_ver=sh(script:'grep DEVKIT_NAME pipelines/build/devkit/'+params.VERSION+'/build/devkit/result/'+devkit_target+'-to-'+devkit_target+'/devkit.info | cut -d"=" -f2 | tr -d "\\" \\n"', returnStdout: true)

        def adoptium_devkit_release_tag = "${gcc_ver}-${devkit_target}-${params.BUILD}"

        // Store Adoptium metadata within the devkit.info file
        sh(script:"echo ADOPTIUM_DEVKIT_RELEASE_TAG=\"${adoptium_devkit_release_tag}\" >> pipelines/build/devkit/${params.VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target}/devkit.info")

        def devkit_tarball = "workspace/devkit-${adoptium_devkit_release_tag}.tar.xz"
        println "devkit artifact filename = ${devkit_tarball}"
 
        // Compress and archive
        sh(script:"tar -cf - -C pipelines/build/devkit/${params.VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target} . | GZIP=-9 xz -c > ${devkit_tarball}")

        // Create sha256.txt
        sh(script:"sha256sum ${devkit_tarball} > ${devkit_tarball}.sha256.txt")

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

