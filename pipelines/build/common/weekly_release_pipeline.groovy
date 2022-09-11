import groovy.json.*

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
stage("Submit Release Pipelines") {
    // Map of <variant> : <scmRef>
    def Map<String, String> scmRefs = new JsonSlurper().parseText("${params.scmReferences}") as Map

    // Map of <platform> : [<variant>,<variant>,..]
    def Map<String, List<String>> targetConfigurations = new JsonSlurper().parseText("${params.targetConfigurations}") as Map

    def Map<String, String> jobs = [:]

    // For each variant create a release pipeline job
    scmRefs.each{ variant ->
        def variantName = variant.key
        def scmRef = variant.value
        def Map<String, List<String>> targetConfig = [:]

        targetConfigurations.each{ target ->
            if (target.value.contains(variantName)) {
                targetConfig.put(target.key,[variantName])
            }
        }

        if (!targetConfig.isEmpty()) {
            echo("Creating ${params.buildPipeline} - ${variantName}")
            jobs[variantName] = {
                stage("Build - ${params.buildPipeline} - ${variantName}") {
                    result = build job: "${params.buildPipeline}",
                            parameters: [
                                string(name: 'releaseType',        value: 'Release'),
                                string(name: 'scmReference',       value: scmRef),
                                text(name: 'targetConfigurations', value: JsonOutput.prettyPrint(JsonOutput.toJson(targetConfig))),
                                ['$class': 'BooleanParameterValue', name: 'keepReleaseLogs', value: false]
                            ]
                    // For reproducible builds (releaseType==Release) to have comparison on multiple builds' artifacts.
                    // Copy artifacts from downstream and archive again on weekly-pipeline. For details, see issue: https://github.com/adoptium/ci-jenkins-pipelines/issues/301
                    if ( result.getCurrentResult() == "SUCCESS" ) {
                        node("worker") {
                            copyArtifacts(
                                projectName: result.getProjectName(), // copy-up
                                selector: specific(result.getNumber().toString()), // buildNumber need to be string not int
                                filter: '**/*.tar.gz, **/*.zip',
                                fingerprintArtifacts: true,
                                flatten: true
                            )
                        }
                    }
                }
            }
        }
    }
    // Run downstream jobs in parallel
    parallel jobs

    archiveArtifacts artifacts: "workspace/target/"
}
