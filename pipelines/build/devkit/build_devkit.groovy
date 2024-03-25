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

// The devkit release tag this build will get published under
// groovylint-disable-next-line
def adoptium_devkit_release_tag

def build_devkit() {
    stage('Build DevKit') {
        // Make DevKit
        sh("cd pipelines/build/devkit && ./make_devkit.sh ${params.VERSION} ${params.ARCH} ${params.BASE_OS} ${params.BASE_OS_VERSION}")

        def devkit_target="${params.ARCH}-linux-gnu"

        // Get gcc version and base OS from devkit.info
        def gcc_ver=sh(script:'grep DEVKIT_NAME pipelines/build/devkit/'+params.VERSION+'/build/devkit/result/'+devkit_target+'-to-'+devkit_target+'/devkit.info | cut -d"=" -f2 | tr -d "\\" \\n"', returnStdout: true)

        // The devkit release tag this build will get published under
        adoptium_devkit_release_tag = "${gcc_ver}-${params.BUILD}"

        def adoptium_devkit_filename = "devkit-${adoptium_devkit_release_tag}-${devkit_target}"

        // Store Adoptium metadata within the devkit.info file
        sh(script:"echo ADOPTIUM_DEVKIT_RELEASE=\"${adoptium_devkit_release_tag}\" >> pipelines/build/devkit/${params.VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target}/devkit.info")
        sh(script:"echo ADOPTIUM_DEVKIT_TARGET=\"${devkit_target}\" >> pipelines/build/devkit/${params.VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target}/devkit.info")

        def devkit_tarball = "${adoptium_devkit_filename}.tar.xz"
        println "devkit artifact filename = ${devkit_tarball}"
 
        // Compress and archive
        sh(script:"tar -cf - -C pipelines/build/devkit/${params.VERSION}/build/devkit/result/${devkit_target}-to-${devkit_target} . | GZIP=-9 xz -c > ${devkit_tarball}")

        // Create sha256.txt
        sh(script:"sha256sum ${devkit_tarball} > ${devkit_tarball}.sha256.txt")

        sh(script:"mkdir workspace && mv adoptium_devkit_filename.* workspace")
        archiveArtifacts artifacts: "workspace/*"
    }
}

def gpgSign() {
    stage('GPG sign') {
        def params = [
                  string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                  string(name: 'UPSTREAM_JOB_NAME',   value: "${env.JOB_NAME}"),
                  string(name: 'UPSTREAM_DIR',        value: '.')
        ]

        def signSHAsJob = build job: 'build-scripts/release/sign_temurin_gpg',
               propagate: true,
               parameters: params

        copyArtifacts(
               projectName: 'build-scripts/release/sign_temurin_gpg',
               selector: specific("${signSHAsJob.getNumber()}"),
               filter: '**/*.sig',
               fingerprintArtifacts: true,
               flatten: true)

        archiveArtifacts artifacts: "*.sig"
    }
}

def dryrunPublish() {
    stage('Dry run publish') {
        println "Running a DRY_RUN publish_devkit_tool for tag: " + adoptium_devkit_release_tag

        def params = [
                  string(name: 'TAG',                 value: "${adoptium_devkit_release_tag}"),
                  string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                  string(name: 'UPSTREAM_JOB_NAME',   value: "${env.JOB_NAME}"),
                  string(name: 'ARTIFACTS_TO_COPY',   value: '*.tar.xz,*.sha256.txt,*.sig'),
                  booleanParam(name: 'DRY_RUN',       value: true)
        ]

        build job: 'build-scripts/release/publish_devkit_tool',
               propagate: true,
               parameters: params
    }
}

node(params.DEVKIT_BUILD_NODE) {
  try {
    cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

    docker.image(params.DOCKER_IMAGE).pull()
    docker.image(params.DOCKER_IMAGE).inside() {
        // Checkout pipelines code
        checkout scm

        build_devkit()

        gpgSign()

        dryrunPublish()
    }
  } finally { 
    cleanWs notFailBuild: true
  } 
}

