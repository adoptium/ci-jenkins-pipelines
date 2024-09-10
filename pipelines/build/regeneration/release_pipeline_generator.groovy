import java.nio.file.NoSuchFileException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/* 
file used as jenkinsfile to generator official release pipeline
*/

// ensure releaseVersions is updated before create releaseTag
def releaseVersions = [8,11,17,21,22,23]


// Regenerate release-openjdkX-pipeline per each jdk version listed in releaseVersions
node('worker') {
    try{
        /*
            use releaseTag's defaults.json for adoptDefaultsJson and defaultsJson
            do not really need to set both,
            but to make jobTemplatePath can handle nightly and release pipeline the same way
        */
        String ADOPT_DEFAULTS_FILE_URL = "https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/${params.releaseTag}/pipelines/defaults.json"

        // Pull in upstream origin ci-jenkins-pipeline defaults json config
        def getAdopt = new URL(ADOPT_DEFAULTS_FILE_URL).openConnection()
        Map<String, ?> ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
        if (!ADOPT_DEFAULTS_JSON || !Map.isInstance(ADOPT_DEFAULTS_JSON)) {
            throw new Exception("[ERROR] No ADOPT_DEFAULTS_JSON found at ${ADOPT_DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file. NOTE: Since this adopt's defaults and unlikely to change location, this is likely a network or GitHub issue.")
        }

        // Pull in User's defined json config
        String DEFAULTS_FILE_URL = (params.DEFAULTS_URL) ?: ADOPT_DEFAULTS_FILE_URL
        def getUser = new URL(DEFAULTS_FILE_URL).openConnection()
        Map<String, ?> DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
        if (!DEFAULTS_JSON || !Map.isInstance(DEFAULTS_JSON)) {
            throw new Exception("[ERROR] No DEFAULTS_JSON found at ${DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file.")
        }

        timestamps {
            def generatedPipelines = []

            String pipelineUrl = "https://github.com/adoptium/ci-jenkins-pipelines.git"
            def releaseTag = params.releaseTag
            checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: releaseTag]], userRemoteConfigs: [[url: pipelineUrl]]]

            String helperRef = params.helperTag ?: params.releaseTag
            library(identifier: "openjdk-jenkins-helper@${helperRef}")

            String aqaRef = params.aqaTag

            // set where generated jobs will be located in the Jenkins
            def jobRoot = DEFAULTS_JSON['jenkinsDetails']['rootDirectory'] // "build-scripts" same as weekly and nightly
            // set where our generation scripts should be located in the ci-jenkins-pipeline repo
            def scriptFolderPath =  DEFAULTS_JSON['scriptDirectories']['upstream'] // same as others: "pipelines/build"
            // set downstream jobs template path in ci-jenkins-pipleine repo
            def jobTemplatePath =  DEFAULTS_JSON['templateDirectories']['release'] // "pipelines/jobs/release_pipeline_job_template.groovy"
            // set where release testconfig should be read out from
            def releaseConfigPath = DEFAULTS_JSON['configDirectories']['release'] // "pipelines/jobs/configurations"

            println '[INFO] Running official release generator script with the following configuration:'
            println "REPOSITORY_URL = ${pipelineUrl}"
            println "REPOSITORY_TAG = ${releaseTag}"
            println "AQA_TEST_TAG = ${aqaRef}"
            println "JOB_ROOT = ${jobRoot}"
            println "SCRIPT_FOLDER_PATH = ${scriptFolderPath}"
            println "RELEASE_CONFIG_PATH = ${releaseConfigPath}"
            println "JOB_TEMPLATE_PATH = ${jobTemplatePath}"
            println "ENABLE_PIPELINE_SCHEDULE = false"
            println "USE_ADOPT_SHELL_SCRIPTS = true"

            releaseVersions.each({ javaVersion ->
                def config = [
                    GIT_URL                     : pipelineUrl,
                    releaseTag                  : releaseTag,
                    aqaTag                      : aqaRef,
                    BUILD_FOLDER                : jobRoot,
                    CHECKOUT_CREDENTIALS        : "",
                    JAVA_VERSION                : javaVersion,
                    JOB_NAME                    : "release-openjdk${javaVersion}-pipeline",
                    SCRIPT                      : "${scriptFolderPath}/openjdk_pipeline.groovy",
                    adoptScripts                : true // USE_ADOPT_SHELL_SCRIPTS
                ]

                def target
                try {
                    def uFile = "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}u_release.groovy"
                    def nonUFile = "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}_release.groovy"
                    if(fileExists(uFile)){
                        target = load uFile
                    } else{
                        target = load nonUFile
                    }              
                } catch (NoSuchFileException e) {
                    throw new Exception("[ERROR] enable to load jdk${javaVersion}u_release.groovy nor jdk${javaVersion}_release.groovy does not exist!")
                }

                // For jdk8u remove aarch32 from the pipeline's target so it does not get built automatically,
                // jdk8u aarch32 is dependent on upstream release in the separate port repository, and will get started manually.
                if (javaVersion == 8 && target.targetConfigurations.containsKey('arm32Linux')) {
                    target.targetConfigurations.remove('arm32Linux')
                }

                config.put('targetConfigurations', target.targetConfigurations)

                config.put('defaultsJson', DEFAULTS_JSON)
                config.put('adoptDefaultsJson', ADOPT_DEFAULTS_JSON)

                if(javaVersion >= 11) { // for jdk11+, need extra config args to pass down
                    config.put('additionalConfigureArgs', "--without-version-pre --without-version-opt")
                }

                println "[INFO] FINAL CONFIG FOR RELEASE JDK${javaVersion}"
                println JsonOutput.prettyPrint(JsonOutput.toJson(config))

                // Create the release job
                try {
                    jobDsl targets: jobTemplatePath, ignoreExisting: false, additionalParameters: config
                } catch (Exception e) {
                    throw new Exception("[ERROR] ${e.message} ... Cannot create release pipeline for jdk${javaVersion}...")
                }
                // add sucessfully generated pipeline into list
                generatedPipelines.add(config['JOB_NAME'])
            })

            // Fail if nothing was generated
            if (generatedPipelines == []) {
                throw new Exception('[ERROR] NO PIPELINES WERE GENERATED!')
            } else {
                println "[SUCCESS] THE FOLLOWING release PIPELINES WERE GENERATED IN THE ${jobRoot} FOLDER:\n${generatedPipelines}"
            }

            releaseVersions.each({ javaVersion ->
                def uFile = "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}u_release.groovy"
                def nonUFile = "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}_release.groovy"
                def jobName
                if(fileExists(uFile)){
                        jobName = "build-scripts/utils/release_pipeline_jobs_generator_jdk${javaVersion}u"
                }
                if(fileExists(nonUFile)){
                        jobName = "build-scripts/utils/release_pipeline_jobs_generator_jdk${javaVersion}"
                }
                def releaseBuildJob = build job: jobName, propagate: false, wait: true, parameters: [['$class': 'StringParameterValue', name: 'REPOSITORY_BRANCH', value: params.releaseTag]]
                if (releaseBuildJob.getResult() == 'SUCCESS') {
                    println "[SUCCESS] jdk${javaVersion} release downstream build jobs are created"
                } else {
                    println "[FAILURE] Failed to create jdk${javaVersion} release downstream build jobs"
                    currentBuild.result = 'FAILURE'
                }
            })
        }
    } finally {
        // Always clean up, even on failure (doesn't delete the created jobs)
        println '[INFO] Cleaning up...'
        cleanWs deleteDirs: true
    }
}
