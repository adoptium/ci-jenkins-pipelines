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

Description: Verifies the upstream job artifacts are signed and notarized as expected.

Parameters:
  - UPSTREAM_JOB_NAME    : Upstream job name containing artifacts
  - UPSTREAM_JOB_NUMBER  : Upstream job number containing artifacts
  - TARGET_OS            : "mac" or "windows"
  - MAC_VERIFY_LABEL     : Jenkins label for where to run "mac"
  - WINDOWS_VERIFY_LABEL : Jenkins label for where to run "windows"

*/


def verify = false
def verifyNode
switch(params.TARGET_OS) {
    case 'mac':
        verifyNode = params.MAC_VERIFY_LABEL
        verify = true
        break
    case 'windows':
        verifyNode = params.WINDOWS_VERIFY_LABEL
        verify = true
        break
    default:
        println "No signing verification for: ${params.TARGET_OS}"
}

if (verify) {
    println "Verifying signing for platform ${params.TARGET_OS}, ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"

    // Switch to appropriate node
    stage("verify_signing") {
        node(verifyNode) {
            try {
                // Clean workspace to ensure no old artifacts
                cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

                // Find upstream job archives to be verified for Signatures
                def jdkFilter
                def jreFilter
                if (params.TARGET_OS == "mac") {
                    jdkFilter = "workspace/target/*-jdk*.tar.gz"
                    jreFilter = "workspace/target/*-jre*.tar.gz"
                } else { // Windows
                    jdkFilter = "workspace/target/*-jdk*.zip"
                    jreFilter = "workspace/target/*-jre*.zip"
                }

                println "[INFO] Retrieving ${jdkFilter} artifacts from ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"
                copyArtifacts(
                    projectName: "${params.UPSTREAM_JOB_NAME}",
                    selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                    filter: "${jdkFilter}",
                    fingerprintArtifacts: true,
                    flatten: true
                )
                println "[INFO] Retrieving ${jreFilter} artifacts from ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"
                copyArtifacts(
                    projectName: "${params.UPSTREAM_JOB_NAME}",
                    selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                    filter: "${jreFilter}",
                    fingerprintArtifacts: true,
                    flatten: true
                ) 

                // For Mac we need to also verify pkg files are "Notarized" if installers have been created
                if (params.TARGET_OS == "mac") {
                    println "[INFO] Retrieving workspace/target/*.pkg artifacts from ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"
                    copyArtifacts(
                        projectName: "${params.UPSTREAM_JOB_NAME}",
                        selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                        filter: "workspace/target/*.pkg",
                        fingerprintArtifacts: true,
                        flatten: true,
                        optional: true
                    )
                }

                // Unpack archives
                def unpack_dir = "unpacked"
                def archives = ["jdk", "jre"]

                archives.each { archive ->
                    def dir = "${unpack_dir}/${archive}"
                    if (params.TARGET_OS == "mac") {
                        sh("mkdir -p ${dir} && tar -C ${dir} -xf *-${archive}*.tar.gz")
                    } else { // Windows
                        sh("mkdir -p ${dir} && unzip *-${archive}*.zip -d ${dir}")
                    }
                }

                // Copy JDK so it can be used for unpacking using jmod/jimage
                sh("mkdir jdk_cp && cp -r ${unpack_dir}/jdk/*/* jdk_cp")

                def jdk_bin = "${WORKSPACE}/jdk_cp/bin"
                if (params.TARGET_OS == "mac") {
                    jdk_bin = "${WORKSPACE}/jdk_cp/Contents/Home/bin"
                }

                // Expand the JMODs and modules image to test binaries within
                archives.each { archive ->
                    def dir = "${unpack_dir}/${archive}"
                    // Expand JMODs
                    println "Expanding JMODS under ${dir}"
                    def jmods = findFiles(glob: "${dir}/**/*.jmod")
                    jmods.each { jmod ->
                        def expand_dir = "expanded_" + sh(script:"basename ${jmod}", returnStdout:true)
                        expand_dir = "${dir}/${expand_dir}".trim()
                        sh("mkdir ${expand_dir}")
                        sh("${jdk_bin}/jmod extract --dir ${expand_dir} ${jmod}")
                    }

                    // Expand "modules" compress image containing jmods
                    println "Expanding 'modules' compressed image file under ${dir}"
                    def modules = findFiles(glob: "${dir}/**/modules")
                    modules.each { module ->
                        def expand_dir = "expanded_" + sh(script:"basename ${module}", returnStdout:true)
                        expand_dir = "${dir}/${expand_dir}".trim()
                        sh("mkdir ${expand_dir}")
                        sh("${jdk_bin}/jimage extract --dir ${expand_dir} ${module}")
                    }
                }

                // Verify all executables for Signatures
                if (params.TARGET_OS == "mac") {
                    // On Mac find all dylib's and binaries marked as "executable",
                    // also add "jpackageapplauncher" specific case which is not marked as "executable"
                    // as it is within the jdk.jpackage resources used by jpackage util to generate user app launchers
                    def bins = sh(script:"find ${unpack_dir} -perm +111 -type f -not -name '.*' -o -name '*.dylib' -o -name 'jpackageapplauncher' || \
                                          find ${unpack_dir} -perm /111 -type f -not -name '.*' -o -name '*.dylib' -o -name 'jpackageapplauncher'",  \
                                  returnStdout:true).split("\\r?\\n|\\r")
                    bins.each { bin ->
                       def rc = sh(script:"codesign --verify --verbose ${bin}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: dylib not signed: ${bin}"
                           currentBuild.result = 'FAILURE'
                       } else {
                           println "Signed correctly: ${bin}"
                       }
                    }
                } else { // Windows
                    // Find all exe/dll's that must be Signed
                    def bins = sh(script:"find ${unpack_dir} -type f -name '*.exe' -o -name '*.dll'", \
                                  returnStdout:true).split("\\r?\\n|\\r")
                    bins.each { bin ->
                       def rc = sh(script:"signtool verify /v ${bin}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: binary not signed: ${bin}"
                           currentBuild.result = 'FAILURE'
                       } else { 
                           println "Signed correctly: ${bin}"
                       } 
                    }
                }

                // For Mac also verify installer (if built) is Notarized
                if (params.TARGET_OS == "mac") {
                    // Find all pkg's that need to be Notarized
                    def pkgs = findFiles(glob: "*.pkg")
                    pkgs.each { pkg ->
                       def rc = sh(script:"spctl -a -vvv -t install ${pkg}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: pkg not Notarized: ${pkg}"
                           currentBuild.result = 'FAILURE'
                       } else {
                           println "Notarized correctly: ${pkg}"
                       }
                    }
                }
            } finally {
                // Clean workspace afterwards
                cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
            }
        }
    }
}

