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


Boolean verify = false
String  verifyNode
switch(params.TARGET_OS) {
    'mac':
        verifyNode = params.MAC_VERIFY_LABEL
        verify = true
        break
    'windows':
        verifyNode = params.WINDOWS_VERIFY_LABEL
        verify = true
        break
    default:
        println "No signing verification for: ${params.TARGET_OS}"
}

if (verify) {
    println "Verifying signing for platform ${params.TARGET_OS}, ${job params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"

    // Switch to appropriate node
    node(verifyNode) {
        timestamps {
                // Clean workspace to ensure no old artifacts
                context.cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

                def jdkFilter
                def jreFilter
                if (params.TARGET_OS == "mac") {
                    jdkFilter = "workspace/target/*-jdk*.tar.gz"
                    jreFilter = "workspace/target/*-jre*.tar.gz"
                } else { // Windows
                    jdkFilter = "workspace/target/*-jdk*.zip"
                    jreFilter = "workspace/target/*-jre*.zip"
                }

                println "[INFO] Retrieving ${jdkFilter} artifacts from ${job params.UPSTREAM_JOB_NAME}/${params.UPSTREAM_JOB_NUMBER}"
                copyArtifacts(
                    projectName: "${job params.UPSTREAM_JOB_NAME}",
                    selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                    filter: "${jdkFilter}",
                    fingerprintArtifacts: true,
                    flatten: true
                )
                println "[INFO] Retrieving ${jreFilter} artifacts from ${job params.UPSTREAM_JOB_NAME}/${params.UPSTREAM_JOB_NUMBER}"
                copyArtifacts(
                    projectName: "${job params.UPSTREAM_JOB_NAME}",
                    selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                    filter: "${jreFilter}",
                    fingerprintArtifacts: true,
                    flatten: true

                // For Mac we need to also verify pkg files are "Notarized"
                if (params.TARGET_OS == "mac") {
                    println "[INFO] Retrieving workspace/target/*.pkg artifacts from ${job params.UPSTREAM_JOB_NAME}/${params.UPSTREAM_JOB_NUMBER}"
                    copyArtifacts(
                        projectName: "${job params.UPSTREAM_JOB_NAME}",
                        selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                        filter: "workspace/target/*.pkg",
                        fingerprintArtifacts: true,
                        flatten: true
                }

                // Unpack archives
                if (params.TARGET_OS == "mac") {
                    context.sh("mkdir jdk && tar -C jdk *-jdk*.tar.gz")
                    context.sh("mkdir jre && tar -C jre *-jre*.tar.gz")
                } else { // Windows
                    context.sh("mkdir jdk && unzip *-jdk*.tar.gz -d jdk")
                    context.sh("mkdir jre && unzip *-jre*.tar.gz -d jre")
                }

                // Copy JDK so it can be used for unpacking
                context.sh("cp -r jdk jdk_cp")

                def jdk_bin = "${WORKSPACE}/jdk_cp/bin"
                if (params.TARGET_OS == "mac") {
                    jdk_bin = "${WORKSPACE}/jdk_cp/Contents/Home/bin"
                }

                withEnv(['PATH+JAVA=${jdk_bin}']) {
                    def folders = ["jdk", "jre"]
                    folders.each { folder ->
                        // Expand JMODs
                        context.println "Expanding JMODS under ${folder}"
                        def jmods = findFiles(glob: "${folder}/**/*.jmod")
                        jmods.each { jmod ->
                            def expand_dir = "expanded_" + context.sh(script:"basename ${jmod}", returnStdout:true)
                            context.sh("mkdir ${expand_dir} && jmod extract --dir ${expand_dir} ${jmod}")
                        }

                        // Expand "modules" compress image containing jmods
                        context.println "Expanding 'modules' compressed image file under ${folder}"
                        def modules = findFiles(glob: "${folder}/**/modules")
                        modules.each { module ->
                            def expand_dir = "expanded_" + context.sh(script:"basename ${module}", returnStdout:true)
                            context.sh("mkdir ${expand_dir} && jimage extract --dir ${expand_dir} ${module}")
                        }
                    }
                }

                if (params.TARGET_OS == "mac") {
                    // On Mac find all dylib's and binaries marked as "executable",
                    // also add "jpackageapplauncher" specific case which is not marked as "executable"
                    // as it is within the jdk.jpackage resources used by jpackage util to generate user app launchers
                    def bins = context.sh(script:"find . -perm +111 -type f -not -name '.*' -o -name '*.dylib' || find . -perm /111 -type f -not -name '.*' -o -name '*.dylib'", returnStdout:true)
                    bins.each { bin ->
                       def rc = context.sh(script:"codesign --verify --verbose ${bin}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: dylib not signed: ${bin}"
                           currentBuild.result = 'FAILURE'
                       } else {
                           println "Signed correctly: ${bin}"
                       }
                    }

                    // Find all pkg's that need to be Notarized
                    def pkgs = findFiles(glob: "*.pkg")
                    pkgs.each { pkg ->
                       def rc = context.sh(script:"spctl -a -vvv -t install ${pkg}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: pkg not Notarized: ${pkg}"
                           currentBuild.result = 'FAILURE'
                       } else {
                           println "Notarized correctly: ${pkg}"
                       }
                    }
                } else { // Windows
                    // Find all exe/dll's that must be Signed
                    def bins = findFiles(glob: "**/*.exe")
                    bins.addAll(findFiles(glob: "**/*.dll"))
                    bins.each { bin ->
                       def rc = context.sh(script:"signtool verify /v ${bin}", returnStatus:true)
                       if (rc != 0) {
                           println "Error: binary not signed: ${bin}"
                           currentBuild.result = 'FAILURE'
                       } else { 
                           println "Signed correctly: ${bin}"
                       } 
                    }
                }
        }
    }
}

