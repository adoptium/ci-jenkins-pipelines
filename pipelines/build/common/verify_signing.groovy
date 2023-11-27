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
  - TARGET_ARCH          : "aarch64 or "x64" or "x86-32"
  - NODE_LABEL           : Jenkins label for where to run

*/

// For Windows find the Windows Kit "signtool.exe", which should reside
// under the default c:\Program Files (x86)\Windows Kit directory
String find_signtool() {
    def arch
    switch (params.TARGET_ARCH) {
        case "aarch64": arch = "arm64"; break
        case "x64":     arch = "x64"; break
        case "x86-32":  arch = "x86-32"; break
        default:
            println "ERROR: Unknown architecture: ${params.TARGET_ARCH}"
            exit 1
    }

    def windowsKitPath = "/cygdrive/c/'Program Files (x86)'/'Windows Kits'"

    def files = sh(script:"find ${windowsKitPath} -type f -path */${arch}/signtool.exe", \
                   returnStdout:true).split("\\r?\\n|\\r")

    // Return the first one we find
    if (files.size() == 0 || files[0].trim() == "") {
        println "ERROR: Unable to find signtool.exe in ${windowsKitPath}"
        exit 2
    } else {
        return files[0].trim()
    }
}

// Unpack the archives so the signartures can be checked
void unpackArchives(String unpack_dir, String[] archives) {
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

    def jdk_bin = "jdk_cp/bin"
    if (params.TARGET_OS == "mac") {
        jdk_bin = "jdk_cp/Contents/Home/bin"
    }

    // Expand the JMODs and modules image to test binaries within
    archives.each { archive ->
        def dir = "${unpack_dir}/${archive}"
        // Expand JMODs
        println "Expanding JMODS under ${dir}"
        def jmods = sh(script:"find ${dir} -type f -name '*.jmod'", \
                       returnStdout:true).split("\\r?\\n|\\r")
        jmods.each { jmod ->
            if (jmod.trim() != "") {
                def expand_dir = "expanded_" + sh(script:"basename ${jmod}", returnStdout:true)
                expand_dir = "${dir}/${expand_dir}".trim()
                sh("mkdir ${expand_dir}")
                sh("${jdk_bin}/jmod extract --dir ${expand_dir} ${jmod}")
            }
        }

        // Expand "modules" compress image containing jmods
        println "Expanding 'modules' compressed image file under ${dir}"
        def modules = sh(script:"find ${dir} -type f -name 'modules'", \
                         returnStdout:true).split("\\r?\\n|\\r")
        modules.each { module ->
            if (module.trim() != "") {
                def expand_dir = "expanded_" + sh(script:"basename ${module}", returnStdout:true)
                expand_dir = "${dir}/${expand_dir}".trim()
                sh("mkdir ${expand_dir}")
                sh("${jdk_bin}/jimage extract --dir ${expand_dir} ${module}")
            }
        }
    }
}

// Verify executables for Signatures
def verifyExecutables(String unpack_dir) {
    if (params.TARGET_OS == "mac") {
        // On Mac find all dylib's and binaries marked as "executable",
        // also add "jpackageapplauncher" specific case which is not marked as "executable"
        // as it is within the jdk.jpackage resources used by jpackage util to generate user app launchers
        def bins = sh(script:"find ${unpack_dir} -perm +111 -type f -not -name '.*' -o -name '*.dylib' -o -name 'jpackageapplauncher' || \
                              find ${unpack_dir} -perm /111 -type f -not -name '.*' -o -name '*.dylib' -o -name 'jpackageapplauncher'",  \
                      returnStdout:true).split("\\r?\\n|\\r")
        bins.each { bin ->
            if (bin.trim() != "") {
                def rc = sh(script:"codesign --verify --verbose ${bin}", returnStatus:true)
                if (rc != 0) {
                    println "Error: executable not Signed: ${bin}"
                    currentBuild.result = 'FAILURE'
                } else {
                    println "Signed correctly: ${bin}"
                }
            }
        }
    } else { // Windows
        def signtool = find_signtool()

        // Find all exe/dll's that must be Signed
        def bins = sh(script:"find ${unpack_dir} -type f -name '*.exe' -o -name '*.dll'", \
                      returnStdout:true).split("\\r?\\n|\\r")
        bins.each { bin ->
            if (bin.trim() != "") {
                def rc = sh(script:"${signtool} verify /pa /v ${bin}", returnStatus:true)
                if (rc != 0) {
                    println "Error: executable not Signed: ${bin}"
                    currentBuild.result = 'FAILURE'
                } else {
                    println "Signed correctly: ${bin}"
                }
            }
        }
    }
}

// Verify installers for Signatures and Notarization(mac only)
def verifyInstallers() {
    if (params.TARGET_OS == "mac") {
        // Find all pkg's that need to be Signed and Notarized
        def pkgs = sh(script:"find . -type f -name '*.pkg'", \
                      returnStdout:true).split("\\r?\\n|\\r")
        pkgs.each { pkg ->
            if (pkg.trim() != "") {
                def rc = sh(script:"pkgutil --check-signature ${pkg}", returnStatus:true)
                if (rc != 0) {
                    println "Error: pkg not Signed: ${pkg}"
                    currentBuild.result = 'FAILURE'
                } else {
                    println "Signed correctly: ${pkg}"
                }

                rc = sh(script:"spctl -a -vvv -t install ${pkg}", returnStatus:true)
                if (rc != 0) {
                    println "Error: pkg not Notarized: ${pkg}"
                    currentBuild.result = 'FAILURE'
                } else {
                    println "Notarized correctly: ${pkg}"
                }
            }
        }
    } else { // Windows
        // Find all msi's that need to be Signed
        def signtool = find_signtool()

        def msis = sh(script:"find . -type f -name '*.msi'", \
                      returnStdout:true).split("\\r?\\n|\\r")
        msis.each { msi ->
            if (msi.trim() != "") {
                def rc = sh(script:"${signtool} verify /pa /v ${msi}", returnStatus:true)
                if (rc != 0) {
                    println "Error: installer not Signed: ${msi}"
                    currentBuild.result = 'FAILURE'
                } else {
                    println "Signed correctly: ${msi}"
                }
            }
        }
    }
}

//
// Main code
//
if (params.TARGET_OS != "mac" && params.TARGET_OS != "windows") {
    println "No signing verification for platform: ${params.TARGET_OS}"
} else {
    println "Verifying signing for platform ${params.TARGET_OS}, ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"

    // Switch to appropriate node
    stage("verify signatures") {
        node(params.NODE_LABEL) {
            try {
                // Clean workspace to ensure no old artifacts
                cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

                // Find upstream job archives to be verified for Signatures
                def jdkFilter
                def jreFilter
                def installerFilter
                if (params.TARGET_OS == "mac") {
                    jdkFilter = "workspace/target/*-jdk*.tar.gz"
                    jreFilter = "workspace/target/*-jre*.tar.gz"
                    installerFilter = "workspace/target/*.pkg"
                } else { // Windows
                    jdkFilter = "workspace/target/*-jdk*.zip"
                    jreFilter = "workspace/target/*-jre*.zip"
                    installerFilter = "workspace/target/*.msi"
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

                println "[INFO] Retrieving ${installerFilter} artifacts from ${params.UPSTREAM_JOB_NAME} #${params.UPSTREAM_JOB_NUMBER}"
                copyArtifacts(
                    projectName: "${params.UPSTREAM_JOB_NAME}",
                    selector: specific("${params.UPSTREAM_JOB_NUMBER}"),
                    filter: "${installerFilter}",
                    fingerprintArtifacts: true,
                    flatten: true,
                    optional: true
                )

                // Unpack archives
                String unpack_dir = "unpacked"
                String[] archives = ["jdk", "jre"]
                unpackArchives(unpack_dir, archives)

                // Verify all executables for Signatures
                verifyExecutables(unpack_dir)

                // Verify installers (if built) are Signed and Notarized(mac only)
                verifyInstallers()
            } finally {
                // Clean workspace afterwards
                cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
            }
        }
    }
}

