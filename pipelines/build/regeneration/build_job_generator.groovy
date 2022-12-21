import java.nio.file.NoSuchFileException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

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
    File used for generate downstream build jobs which are triggered by via [release_]pipeline_jobs_generator_jdkX, e.g:
    
    - build-scripts/jobs/jdk11u/jdk11u-linux-arm-temurin (jobType = "nightly")
    - build-scripts/jobs/jdk11u/evaluation-jdk11u-linux-arm-temurin (when jobType = "evaluation")
    - build-scripts/release/jobs/release-jdk17u-mac-x64-temurin (when jobType = "release")
    - build-scripts-pr-tester/build-test/jobs/jdk19u/jdk19u-alpine-linux-x64-temurin (when "pr-tester")
*/

String javaVersion = params.JAVA_VERSION
String ADOPT_DEFAULTS_FILE_URL = 'https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/master/pipelines/defaults.json'
String DEFAULTS_FILE_URL = (params.DEFAULTS_URL) ?: ADOPT_DEFAULTS_FILE_URL

node('worker') {
    // Retrieve Adopt Defaults
    def getAdopt = new URL(ADOPT_DEFAULTS_FILE_URL).openConnection()
    Map<String, ?> ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
    if (!ADOPT_DEFAULTS_JSON || !Map.isInstance(ADOPT_DEFAULTS_JSON)) {
        throw new Exception("[ERROR] No ADOPT_DEFAULTS_JSON found at ${ADOPT_DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file. NOTE: Since this adopt's defaults and unlikely to change location, this is likely a network or GitHub issue.")
    }

    // Retrieve User Defaults
    def getUser = new URL(DEFAULTS_FILE_URL).openConnection()
    Map<String, ?> DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
    if (!DEFAULTS_JSON || !Map.isInstance(DEFAULTS_JSON)) {
        throw new Exception("[ERROR] No DEFAULTS_JSON found at ${DEFAULTS_FILE_URL}. Please ensure this path is correct and it leads to a JSON or Map object file.")
    }

    try {
        // Load git url and branch and gitBranch. These determine where we will be pulling configs from.
        def repoUri = (params.REPOSITORY_URL) ?: DEFAULTS_JSON['repository']['pipeline_url']
        def repoBranch = (params.REPOSITORY_BRANCH) ?: DEFAULTS_JSON['repository']['pipeline_branch']

        // Load credentials to be used in checking out. This is in case we are checking out a URL that is not Adopts and they don't have their ssh key on the machine.
        def checkoutCreds = (params.CHECKOUT_CREDENTIALS) ?: ''
        def remoteConfigs = new JsonSlurper().parseText('{ "url": "" }') as Map
        remoteConfigs.url = repoUri

        if (checkoutCreds != '') {
            // This currently does not work with user credentials due to https://issues.jenkins.io/browse/JENKINS-60349
            remoteConfigs.credentials = "${checkoutCreds}"
        } else {
            println "[WARNING] CHECKOUT_CREDENTIALS not specified! Checkout to $repoUri may fail if you do not have your ssh key on this machine."
        }

        /*
        Changes dir to Adopt's repo. Use closures as functions aren't accepted inside node blocks
        */
        def checkoutAdoptPipelines = { ->
            checkout([$class: 'GitSCM',
                branches: [ [ name: ADOPT_DEFAULTS_JSON['repository']['pipeline_branch'] ] ],
                userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON['repository']['pipeline_url'] ] ]
            ])
        }

        /*
        Changes dir to the user's repo. Use closures as functions aren't accepted inside node blocks
        */
        def checkoutUserPipelines = { ->
            checkout([$class: 'GitSCM',
                branches: [ [ name: repoBranch ] ],
                userRemoteConfigs: [ remoteConfigs ]
            ])
        }

        String helperRef = DEFAULTS_JSON['repository']['helper_ref']
        library(identifier: "openjdk-jenkins-helper@${helperRef}")

        // Load buildConfigurations from config file. This is what the nightlies & releases use to setup their downstream jobs
        def buildConfigurations = null
        def buildConfigPath = (params.BUILD_CONFIG_PATH) ? "${WORKSPACE}/${BUILD_CONFIG_PATH}" : "${WORKSPACE}/${DEFAULTS_JSON['configDirectories']['build']}"
        
        // Very first time to checkout ci-jenkins-pipeline repo
        checkoutUserPipelines()

        try {
            buildConfigurations = load "${buildConfigPath}/${javaVersion}_pipeline_config.groovy"
        } catch (NoSuchFileException e) {
            try {
                println "[WARNING] ${buildConfigPath}/${javaVersion}_pipeline_config.groovy does not exist, chances are we want a U version..."

                buildConfigurations = load "${buildConfigPath}/${javaVersion}u_pipeline_config.groovy"
                javaVersion += 'u'
            } catch (NoSuchFileException e2) {
                println "[WARNING] U version does not exist. Likelihood is we are generating from a repository that isn't Adopt's. Pulling Adopt's build config in..."

                checkoutAdoptPipelines()
                try {
                    buildConfigurations = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaVersion}_pipeline_config.groovy"
                } catch (NoSuchFileException e3) {
                    buildConfigurations = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaVersion}u_pipeline_config.groovy"
                    javaVersion += 'u'
                }
                checkoutUserPipelines()
            }
        }

        if (buildConfigurations == null) {
            throw new Exception("[ERROR] Could not find buildConfigurations for ${javaVersion}")
        }

        /*
            handle different type of downstream job: release, evaluation, nightly
            could set to a "pr-tester" but most of the logic is same to nightly, wont gain much
        */
        String jobType = ""
        def jobRoot = (params.JOB_ROOT) ?: DEFAULTS_JSON['jenkinsDetails']['rootDirectory']
        if (jobRoot.contains('release')) {
            jobType = "release"
            // either use root path or flag from job to determinate if it is evaluation
        } else if (jobRoot.contains('evaluation') || params.IS_EVALUATION_JOB) {
            jobType = "evaluation"
        } else {
            jobType = "nightly"
        }

        def targetConfigFile = (jobType == "nightly") ? "${javaVersion}.groovy" : "${javaVersion}_${jobType}.groovy"
        def targetConfigPath = (params.TARGET_CONFIG_PATH) ? "${WORKSPACE}/${TARGET_CONFIG_PATH}/${targetConfigFile}" : "${WORKSPACE}/${DEFAULTS_JSON['configDirectories'][jobType]}/${targetConfigFile}"
        if (!fileExists(targetConfigPath)) {
            checkoutAdoptPipelines
            targetConfigPath = "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories'][jobType]}/${targetConfigFile}"
        }
        load targetConfigPath
        checkoutUserPipelines

        if (targetConfigurations == null) {
            throw new Exception("[ERROR] Could not find targetConfigurations for ${javaVersion}")
        }

        def jenkinsBuildRoot = (params.JENKINS_BUILD_ROOT) ?: "${DEFAULTS_JSON['jenkinsDetails']['rootUrl']}/job/${jobRoot}/"

        def jobTemplatePath = (params.JOB_TEMPLATE_PATH) ?: DEFAULTS_JSON['templateDirectories']['downstream']
        if (!fileExists(jobTemplatePath)) {
            println "[WARNING] ${jobTemplatePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
            checkoutAdoptPipelines()
            jobTemplatePath = ADOPT_DEFAULTS_JSON['templateDirectories']['downstream']
            println "[SUCCESS] The path is now ${jobTemplatePath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
            checkoutUserPipelines()
        }

        def scriptPath = (params.SCRIPT_PATH) ?: DEFAULTS_JSON['scriptDirectories']['downstream']
        if (!fileExists(scriptPath)) {
            println "[WARNING] ${scriptPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
            checkoutAdoptPipelines()
            scriptPath = ADOPT_DEFAULTS_JSON['scriptDirectories']['downstream']
            println "[SUCCESS] The path is now ${scriptPath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
            checkoutUserPipelines()
        }

        def baseFilePath = (params.BASE_FILE_PATH) ?: DEFAULTS_JSON['baseFileDirectories']['downstream']
        if (!fileExists(baseFilePath)) {
            println "[WARNING] ${baseFilePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
            checkoutAdoptPipelines()
            baseFilePath = ADOPT_DEFAULTS_JSON['baseFileDirectories']['downstream']
            println "[SUCCESS] The path is now ${baseFilePath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
            checkoutUserPipelines()
        }

        def excludes = (params.EXCLUDES_LIST) ?: ''
        def jenkinsCreds = (params.JENKINS_AUTH) ?: ''
        Integer sleepTime = 900
        if (params.SLEEP_TIME) {
            sleepTime = SLEEP_TIME as Integer
        }

        println '[INFO] Running regeneration script with the following configuration:'
        println "VERSION: $javaVersion"
        println "CI REPOSITORY URL: $repoUri"
        println "CI REPOSITORY BRANCH: $repoBranch"
        println "BUILD CONFIGURATIONS: ${JsonOutput.prettyPrint(JsonOutput.toJson(buildConfigurations))}"
        println "JOBS TO GENERATE: ${JsonOutput.prettyPrint(JsonOutput.toJson(targetConfigurations))}"
        println "JOB ROOT: $jobRoot"
        println "JENKINS ROOT: $jenkinsBuildRoot"
        println "JOB TEMPLATE PATH: $jobTemplatePath"
        println "SCRIPT PATH: $scriptPath"
        println "BASE FILE PATH: $baseFilePath"
        println "EXCLUDES LIST: $excludes"
        println "SLEEP_TIME: $sleepTime"
        println "JOB TYPE: $jobType"
        
        // Load regen script and execute base file
        Closure regenerationScript
        def regenScriptPath = (params.REGEN_SCRIPT_PATH) ?: DEFAULTS_JSON['scriptDirectories']['regeneration']
        try {
            regenerationScript = load "${WORKSPACE}/${regenScriptPath}"
        } catch (NoSuchFileException e) {
            println "[WARNING] ${regenScriptPath} does not exist in your chosen repository. Using adopt's script path instead"
            checkoutAdoptPipelines()
            regenerationScript = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['scriptDirectories']['regeneration']}"
            checkoutUserPipelines()
        }

        if (jenkinsCreds != '') {
            withCredentials([usernamePassword(
                credentialsId: "${JENKINS_AUTH}",
                usernameVariable: 'jenkinsUsername',
                passwordVariable: 'jenkinsToken'
            )]) {
                String jenkinsCredentials = "$jenkinsUsername:$jenkinsToken"
                regenerationScript(
                    javaVersion,
                    buildConfigurations,
                    targetConfigurations,
                    DEFAULTS_JSON,
                    excludes,
                    sleepTime,
                    currentBuild,
                    this,
                    jobRoot,
                    remoteConfigs,
                    repoBranch,
                    jobTemplatePath,
                    baseFilePath,
                    scriptPath,
                    jenkinsBuildRoot,
                    jenkinsCredentials,
                    checkoutCreds,
                    jobType
                ).regenerate()
            }
        } else {
            println '[WARNING] No Jenkins API Credentials have been provided! If your server does not have anonymous read enabled, you may encounter 403 api request error code.'
            regenerationScript(
                javaVersion,
                buildConfigurations,
                targetConfigurations,
                DEFAULTS_JSON,
                excludes,
                sleepTime,
                currentBuild,
                this,
                jobRoot,
                remoteConfigs,
                repoBranch,
                jobTemplatePath,
                baseFilePath,
                scriptPath,
                jenkinsBuildRoot,
                jenkinsCreds,
                checkoutCreds,
                jobType
            ).regenerate()
        }
        println '[SUCCESS] All done!'
    } finally {
        // Always clean up, even on failure (doesn't delete the generated jobs)
        println '[INFO] Cleaning up...'
        cleanWs deleteDirs: true
    }
}
