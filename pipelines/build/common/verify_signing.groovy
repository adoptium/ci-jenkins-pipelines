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
        case "x86-32":  arch = "x86"; break
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
        def signtool = files[0].trim().replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)").replaceAll("\\ ","\\\\ ")
        println "Found signtool: ${signtool}"
        return signtool 
    }
}

// Unpack the archives so the signatures can be checked
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
        println "Expanding JMODS and 'modules' under ${dir}"

        withEnv(['dir='+dir, 'jdk_bin='+jdk_bin]) {
            // groovylint-disable
            sh '''
                #!/bin/bash
                set -eu
                FILES=$(find "${dir}" -type f -name '*.jmod')
                for f in $FILES
                do
                    expand_dir=$(basename ${f})
                    expand_dir="${dir}/expanded_${expand_dir}"
                    mkdir "${expand_dir}"
                    echo "Expanding JMOD ${f}"
                    ${jdk_bin}/jmod extract --dir ${expand_dir} ${f}
                done

                FILES=$(find "${dir}" -type f -name 'modules')
                for f in $FILES
                do  
                    expand_dir=$(basename ${f})
                    expand_dir="${dir}/expanded_${expand_dir}"
                    mkdir "${expand_dir}"
                    echo "Expanding compressed image file ${f}"
                    ${jdk_bin}/jimage extract --dir ${expand_dir} ${f}
                done
            '''
        }
    }
}

// Verify executables for Signatures
void verifyExecutables(String unpack_dir) {
    if (params.TARGET_OS == "mac") {
        // On Mac find all dylib's and "executable" binaries
        // Ignore "legal" text folder to reduce the number of non-extension files it finds...

        withEnv(['unpack_dir='+unpack_dir]) {
            // groovylint-disable
            sh '''
                #!/bin/bash
                set -eu
                unsigned=""
                cc_signed=0
                cc_unsigned=0
                FILES=$(find "${unpack_dir}" -type f -not -name '*.*' -not -path '*/legal/*' -o -type f -name '*.dylib')
                for f in $FILES
                do
                    # Is file a Mac 64 bit executable or dylib ?
                    if file ${f} | grep "Mach-O 64-bit executable\\|Mach-O 64-bit dynamically linked shared library" >/dev/null; then
                        if ! codesign --verify --verbose ${f}; then
                            echo "Error: executable not Signed: ${f}"
                            unsigned="$unsigned $f"
                            cc_unsigned=$((cc_unsigned+1))
                        else
                            # Verify it is not "adhoc" signed
                            if ! codesign --display --verbose ${f} 2>&1 | grep Signature=adhoc; then
                                echo "Signed correctly: ${f}"
                                cc_signed=$((cc_signed+1))
                            else
                                echo "Error: executable is 'adhoc' Signed: ${f}"
                                unsigned="$unsigned $f"
                                cc_unsigned=$((cc_unsigned+1))
                            fi
                        fi
                    fi
                done

                if [ "x${unsigned}" != "x" ]; then
                    echo "FAILURE: The following ${cc_unsigned} executables are not signed correctly:"
                    for f in $unsigned
                    do
                        echo "    ${f}"
                    done
                    exit 1
                else
                    echo "SUCCESS: ${cc_signed} executables are correctly signed"
                fi
            '''
        }
    } else { // Windows
        def signtool = find_signtool()

        // Find all exe/dll's that must be Signed

        withEnv(['unpack_dir='+unpack_dir]) {
            // groovylint-disable
            sh '''
                #!/bin/bash
                set -eu
                unsigned=""
                cc_signed=0
                cc_unsigned=0
                FILES=$(find ${unpack_dir} -type f -name '*.exe' -o -name '*.dll')
                for f in $FILES
                do
                    if ! ${signtool} verify /pa /v ${f}; then
                        echo "Error: executable not Signed: ${f}"
                        unsigned="$unsigned $f"
                        cc_unsigned=$((cc_unsigned+1))
                    else
                        echo "Signed correctly: ${f}"
                        cc_signed=$((cc_signed+1))
                    fi
                done

                if [ "x${unsigned}" != "x" ]; then
                    echo "FAILURE: The following ${cc_unsigned} executables are not signed correctly:"
                    for f in $unsigned
                    do
                        echo "    ${f}"
                    done
                    exit 1
                else
                    echo "SUCCESS: ${cc_signed} executables are correctly signed"
                fi
            '''
        }
    }
}

// Verify installers for Signatures and Notarization(mac only)
void verifyInstallers() {
    if (params.TARGET_OS == "mac") {
        // Find all pkg's that need to be Signed and Notarized

        withEnv(['unpack_dir='+unpack_dir]) {
            // groovylint-disable
            sh '''
                #!/bin/bash
                set -eu
                unsigned=""
                cc_signed=0
                cc_unsigned=0
                FILES=$(find . -type f -name '*.pkg')
                for f in $FILES
                do
                    if ! pkgutil --check-signature ${f}; then
                        echo "Error: pkg not Signed: ${f}"
                        unsigned="$unsigned $f"
                        cc_unsigned=$((cc_unsigned+1))
                    else
                        echo "Signed correctly: ${f}"

                        if ! spctl -a -vvv -t install ${f}"; then
                            echo "Error: pkg not Notarized: ${f}"
                            unsigned="$unsigned $f"
                            cc_unsigned=$((cc_unsigned+1))
                        else
                            echo "Notarized correctly: ${f}"
                            cc_signed=$((cc_signed+1))
                        fi
                    fi
                done 

                if [ "x${unsigned}" != "x" ]; then
                    echo "FAILURE: The following ${cc_unsigned} installers are not signed and notarized correctly:"
                    for f in $unsigned
                    do
                        echo "    ${f}"
                    done
                    exit 1
                else
                    echo "SUCCESS: ${cc_signed} installers are correctly signed and notarized"
                fi
            ''' 
        }
    } else { // Windows
        // Find all msi's that need to be Signed
        def signtool = find_signtool()

        withEnv(['unpack_dir='+unpack_dir]) {
            // groovylint-disable
            sh '''
                #!/bin/bash
                set -eu
                unsigned=""
                cc_signed=0
                cc_unsigned=0
                FILES=$(find . -type f -name '*.msi')
                for f in $FILES
                do
                    if ! ${signtool} verify /pa /v ${f}; then
                        echo "Error: installer not Signed: ${f}"
                        unsigned="$unsigned $f"
                        cc_unsigned=$((cc_unsigned+1))
                    else
                        echo "Signed correctly: ${f}"
                        cc_signed=$((cc_signed+1))
                    fi
                done

                if [ "x${unsigned}" != "x" ]; then
                    echo "FAILURE: The following ${cc_unsigned} installers are not signed correctly:"
                    for f in $unsigned
                    do
                        echo "    ${f}"
                    done
                    exit 1
                else
                    echo "SUCCESS: ${cc_signed} installers are correctly signed"
                fi
            '''
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

                println "[INFO] Success, all executables are signed"
            } finally {
                // Clean workspace afterwards
                cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
            }
        }
    }
}

