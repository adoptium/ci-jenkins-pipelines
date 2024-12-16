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
    Signs and verifies SBOMs using the Eclipse signing PEM key
    https://ci.adoptium.net/job/build-scripts/job/release/job/sign_temurin_jsf/

    Parameters:
    UPSTREAM_JOB_NAME           Upstream job name from which to copy unsigned SBOMs
    UPSTREAM_JOB_NUMBER         Job number of UPSTREAM_JOB_NAME to copy artifacts from
    UPSTREAM_DIR                Directory of UPSTREAM_JOB_NAME to copy artifacts from
*/

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

stage('Signing SBOM') {

    node('jsfsign') {

        try {
            
            // Build SBOM Libraries
            println "Kicking off build_sign_sbom_libraries to build SBOM libraries"
            def buildSBOMLibrariesJob = context.build job: 'build_sign_sbom_libraries',
                propagate: true

            // Clean workspace
            println "Cleaning workspace"
            cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true

            println "Copying SBOMs from ${UPSTREAM_JOB_NUMBER} build number ${UPSTREAM_JOB_NUMBER}"
            context.copyArtifacts(
                projectName: "${UPSTREAM_JOB_NAME}",
                selector: context.specific("${UPSTREAM_JOB_NUMBER}"),
                filter: 'workspace/target/*sbom*.json',
                fingerprintArtifacts: true,
                target: 'artifacts',
                flatten: true
                )

            println "Copying JARs from build_sign_sbom_libraries build number ${buildSBOMLibrariesJob.getNumber()}"
            context.copyArtifacts(
                projectName: "build_sign_sbom_libraries",
                selector: context.specific("${buildSBOMLibrariesJob.getNumber()}"),
                filter: 'cyclonedx-lib/build/jar/*.jar',
                fingerprintArtifacts: true,
                target: 'artifacts',
                flatten: true
                )

            def publicKey = "ef-attestation-public"
            def privateKey = "ef-attestation-private"

            withCredentials([file(credentialsId: privateKey, variable: 'PRIVATE_KEY'), file(credentialsId: publicKey, variable: 'PUBLIC_KEY')]) {   
                // Sign SBOMS
                sh ''' 
                    cd artifacts
                    for ARTIFACT in $(find . ( -name *sbom*.json )  | grep -v metadata.json); do
                    echo "Signing ${ARTIFACT}"
                    java -cp "cyclonedx-lib/build/jar/*" temurin.sbom.TemurinSignSBOM --verbose --signSBOM --jsonFile "${ARTIFACT}" --privateKeyFile "$PRIVATE_KEY"

                    echo "Verifying Signature on ${ARTIFACT}"
                    java -cp "cyclonedx-lib/build/jar/*" temurin.sbom.TemurinSignSBOM --verbose --verifySignature --jsonFile "${ARTIFACT}" --publicKeyFile "$PUBLIC_KEY"
                    done
                '''
            }
            context.timeout(time: 1, unit: 'HOURS') {
                archiveArtifacts artifacts: 'artifacts/*sbom*.json'
            }
        }
        catch (FlowInterruptedException e) {
            throw new Exception("[ERROR] Archive artifact timeout 1 HOURS for sign_temurin_jsf has been reached. Exiting...")
        }
        finally {
            cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
        }
    }
}