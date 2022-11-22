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

import groovy.json.JsonSlurper
import java.nio.file.NoSuchFileException

class PullRequestTestPipeline implements Serializable {

    def context
    def currentBuild

    String branch
    String gitRepo
    Map<String, ?> testConfigurations
    Map<String, ?> DEFAULTS_JSON
    List<Integer> javaVersions

    String BUILD_FOLDER = 'build-scripts-pr-tester/build-test'
    String ADOPT_DEFAULTS_FILE_URL = 'https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/master/pipelines/defaults.json'
    def getAdopt = new URL(ADOPT_DEFAULTS_FILE_URL).openConnection()
    Map<String, ?> ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map

    /*
    * Creates a configuration for the top level pipeline job
    */
    Map<String, ?> generateConfig(def javaVersion) {
        return [
                PR_BUILDER          : true,
                TEST                : false,
                GIT_URL             : gitRepo,
                BRANCH              : "${branch}",
                BUILD_FOLDER        : BUILD_FOLDER,
                JAVA_VERSION        : javaVersion,
                JOB_NAME            : "openjdk${javaVersion}-pipeline",
                SCRIPT              : "${DEFAULTS_JSON['scriptDirectories']['upstream']}/openjdk_pipeline.groovy",
                disableJob          : false,
                pipelineSchedule    : '0 0 31 2 0', // 31st Feb so will never run
                targetConfigurations: testConfigurations,
                defaultsJson        : DEFAULTS_JSON,
                adoptDefaultsJson   : ADOPT_DEFAULTS_JSON,
                CHECKOUT_CREDENTIALS: '',
                adoptScripts        : true,
                enableTests         : false,
                enableTestDynamicParallel : false
        ]
    }

    /*
    * Generates the top level pipeline job
    */
    def generatePipelineJob(def javaVersion) {
        context.println '[INFO] Running Pipeline Generation Script...'
        Map<String, ?> config = generateConfig(javaVersion)
        context.checkout([$class: 'GitSCM', userRemoteConfigs: [[url: config.GIT_URL]], branches: [[name: branch]]])

        context.println "JDK${javaVersion} disableJob = ${config.disableJob}"
        context.jobDsl targets: DEFAULTS_JSON['templateDirectories']['upstream'], ignoreExisting: false, additionalParameters: config
    }

    /*
    * Main function, called from the pr tester in jenkins itself
    */
    def runTests() {
        def jobs = [:]
        Boolean pipelineFailed = false

        // Load generation scripts
        context.node('worker') {
            context.println "loading ${context.WORKSPACE}/${DEFAULTS_JSON['scriptDirectories']['regeneration']}"
            Closure regenerationScript = context.load "${context.WORKSPACE}/${DEFAULTS_JSON['scriptDirectories']['regeneration']}"

            javaVersions.each({ javaVersion ->
                // generate top level job
                generatePipelineJob(javaVersion)
                context.println '[INFO] Running downstream jobs regeneration script...'

                // Load platform specific build configs
                def buildConfigurations
                Boolean updateRepo = false
                context.println "loading ${context.WORKSPACE}/${DEFAULTS_JSON['configDirectories']['build']}/jdk${javaVersion}_pipeline_config.groovy"
                try {
                    buildConfigurations = context.load "${context.WORKSPACE}/${DEFAULTS_JSON['configDirectories']['build']}/jdk${javaVersion}_pipeline_config.groovy"
                } catch (NoSuchFileException e) {
                    context.println "[WARNING] ${context.WORKSPACE}/${DEFAULTS_JSON['configDirectories']['build']}/jdk${javaVersion}_pipeline_config.groovy does not exist. Trying jdk${javaVersion}u_pipeline_config.groovy..."

                    buildConfigurations = context.load "${context.WORKSPACE}/${DEFAULTS_JSON['configDirectories']['build']}/jdk${javaVersion}u_pipeline_config.groovy"
                    updateRepo = true
                }

                String actualJavaVersion = updateRepo ? "jdk${javaVersion}u" : "jdk${javaVersion}"
                def excludedBuilds = ''

                // Generate downstream pipeline jobs
                //TODO
                regenerationScript(
                    actualJavaVersion,
                    buildConfigurations,
                    testConfigurations,
                    DEFAULTS_JSON,
                    excludedBuilds,
                    900,
                    currentBuild,
                    context,
                    'build-scripts-pr-tester/build-test',
                    [ url: gitRepo ],
                    branch,
                    DEFAULTS_JSON['templateDirectories']['downstream'],
                    DEFAULTS_JSON['baseFileDirectories']['downstream'],
                    DEFAULTS_JSON['scriptDirectories']['downstream'],
                    'https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/job/build-test',
                    null,
                    null,
                    true
                ).regenerate()

                context.println "[SUCCESS] Regeneration on ${javaVersion} all done!"
            })
            /*
                Handling PR comments:
                run tests                   run all version from  $javaVersions
                run tests quick             run jdk17
                run tests quick 8           run jdk8
                run tests quick 11,17,19    run jdk11, 17 and 19
            */
            String[] commentsList = context.params.ghprbCommentBody.trim().split('run tests quick')
            switch (commentsList.size()) {
                case 0:
                    javaVersions = [17]
                    break
                case 1:
                    javaVersions = javaVersions
                    break
                case 2:
                    javaVersions = commentsList[1].tokenize(',[]').collect { it as int }
                    break
            }
            // Calling build-test/openjdkX-pipeline against PR
            javaVersions.each({ javaVersion ->
                jobs["PR test JDK${javaVersion}"] = {
                    context.stage("Test building Java ${javaVersion}") {
                        try {
                            context.build job: "${BUILD_FOLDER}/openjdk${javaVersion}-pipeline",
                                propagate: true,
                                parameters: [
                                    context.string(name: 'releaseType', value: 'Nightly Without Publish'),
                                    context.string(name: 'activeNodeTimeout', value: '0'),
                                    context.string(name: 'ciReference', value: "${branch}"), // use PR's SHA1 for the generated openjdkX-pipeline
                                    context.booleanParam(name: 'enableTestDynamicParallel', value: false), // not needed unless we enable test
                                    context.booleanParam(name: 'enableInstallers', value: false), // never need this enabled in pr-test
                                    context.booleanParam(name: 'useAdoptBashScripts', value: false), // should not use defaultsJson but adoptDefaultsJson
                                    context.booleanParam(name: 'keepReleaseLogs', value: false) // never need this enabled in pr-test
                                ]
                        } catch (err) {
                            context.println "[ERROR] ${actualJavaVersion} PIPELINE FAILED\n$err"
                            pipelineFailed = true
                        }
                    }
                }
            })
        } // End: node("worker")

        context.parallel jobs

        // Move to "worker" workspace context to perform clean up...
        context.node('worker') {
            // Only clean up the space if the tester passed
            if (!pipelineFailed) {
                context.println '[INFO] Cleaning up...'
                context.cleanWs notFailBuild: true
            } else {
                context.println '[ERROR] Pipelines failed. Setting build result to FAILURE...'
                currentBuild.result = 'FAILURE'
            }
        }
    }

}

Map<String, ?> defaultTestConfigurations = [
    'x64Linux': [
        'temurin'
    ],
    'x64AlpineLinux' : [
        'temurin'
    ],
    'aarch64Linux': [
        'temurin'
    ],
    'x64Windows': [
        'temurin'
    ],
    'x64Mac': [
        'temurin'
    ]
]

List<Integer> defaultJavaVersions = [8, 11, 17, 19]

return {
    String branch,
    def currentBuild,
    def context,
    String gitRepo,
    Map<String, ?> DEFAULTS_JSON,
    String testConfigurations = null,
    String versions = null
        ->
    Map<String, ?> testConfig = defaultTestConfigurations
    List<Integer> javaVersions = defaultJavaVersions
    Map<String, ?> defaultsJson = DEFAULTS_JSON

    if (gitRepo == null) {
        gitRepo = DEFAULTS_JSON['repository']['pipeline_url']
    }

    if (testConfigurations != null) {
        testConfig = new JsonSlurper().parseText(testConfigurations) as Map
    }

    if (versions != null) {
        javaVersions = new JsonSlurper().parseText(versions) as List<Integer>
    }

    return new PullRequestTestPipeline(
            gitRepo: gitRepo,
            branch: branch,
            testConfigurations: testConfig,
            DEFAULTS_JSON: defaultsJson,
            javaVersions: javaVersions,
            context: context,
            currentBuild: currentBuild
        )
}
