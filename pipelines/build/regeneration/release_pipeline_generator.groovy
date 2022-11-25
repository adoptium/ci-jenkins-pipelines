import java.nio.file.NoSuchFileException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ensure releaseVersions is updated before create releaseTag
def releaseVersions = [19] // TODO enable full list when testing done [8,11,17,19]


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

            String helperRef = "https://github.com/adoptium/jenkins-helper.git"
            library(identifier: "openjdk-jenkins-helper@${helperRef}")

            // set where generated jobs will be located in the Jenkins
            def jobRoot = "build-scripts/job/release/"
            // set where our generation scripts should be located in the ci-jenkins-pipeline repo
            def scriptFolderPath = "pipelines/build"
            // set downstream jobs template path in ci-jenkins-pipleine repo
            def jobTemplatePath = "pipelines/jobs/release_pipeline_job_template.groovy"
            // set where release testconfig should be read out from
            def releaseConfigPath = "pipelines/jobs/configurations"

            println '[INFO] Running official release generator script with the following configuration:'
            println "REPOSITORY_URL = ${pipelineUrl}"
            println "REPOSITORY_TAG = ${releaseTag}"
            println "JOB_ROOT = ${jobRoot}"
            println "SCRIPT_FOLDER_PATH = ${scriptFolderPath}"
            println "RELEASE_CONFIG_PATH = ${releaseConfigPath}"
            println "JOB_TEMPLATE_PATH = ${jobTemplatePath}"
            println "ENABLE_PIPELINE_SCHEDULE = false"
            println "USE_ADOPT_SHELL_SCRIPTS = false"

            releaseVersions.each({ javaVersion ->
                def config = [
                    TEST                        : false,
                    GIT_URL                     : pipelineUrl,
                    releaseTag                  : releaseTag,
                    BUILD_FOLDER                : jobRoot,
                    CHECKOUT_CREDENTIALS        : "",
                    JAVA_VERSION                : javaVersion,
                    JOB_NAME                    : "release-openjdk${javaVersion}-pipeline",
                    SCRIPT                      : "${scriptFolderPath}/openjdk_pipeline.groovy",
                    adoptScripts                : false
                ]

                def target
                try {
                    target = load "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}u_release.groovy"
                } catch (NoSuchFileException e) {
                    try {
                        println "[WARNING] jdk${javaVersion}u_release.groovy does not exist, chances are we want a jdk${javaVersion}_release.groovy file. Trying ${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}_release.groovy"
                        target = load "${WORKSPACE}/${releaseConfigPath}/jdk${javaVersion}_release.groovy"
                    } catch (NoSuchFileException e2) {
                         throw new Exception("[ERROR] jdk${javaVersion}_release.groovy does not exist too.... Horrible!")
                    }
                }

                config.put('targetConfigurations', target.targetConfigurations)

                config.put('defaultsJson', DEFAULTS_JSON)
                config.put('adoptDefaultsJson', ADOPT_DEFAULTS_JSON)

                println "[INFO] FINAL CONFIG FOR RELEASE JDK${javaVersion}"
                println JsonOutput.prettyPrint(JsonOutput.toJson(config))

                // Create the release job
                try {
                    jobDsl targets: jobTemplatePath, ignoreExisting: false, additionalParameters: config
                } catch (Exception e) {
                    throw new Exception("[ERROR] Something went wrong when creating the job dsl for jdk${javaVersion}...")
                }
                target.disableJob = false
                // add sucessfully generated pipeline into list
                generatedPipelines.add(config['JOB_NAME'])
            })

            // Fail if nothing was generated
            if (generatedPipelines == []) {
                throw new Exception('[ERROR] NO PIPELINES WERE GENERATED!')
            } else {
                println "[SUCCESS] THE FOLLOWING PIPELINES WERE GENERATED IN THE ${jobRoot} FOLDER"
                println generatedPipelines
            }
        }
    } finally {
        // Always clean up, even on failure (doesn't delete the created jobs)
        println '[INFO] Cleaning up...'
        cleanWs deleteDirs: true
    }
}

// Calling release-pipeline_jobs_generator_jdk11X per each jdk version listed in releaseVersions
node('worker') {
    releaseVersions.each({ javaVersion ->
        def jobName = "releasepipeline/release_pipeline_jobs_generator_jdk${javaVersion}u"
        def releaseBuildJob = build job: jobName, propagate: false, wait: true, parameters: [['$class': 'StringParameterValue', name: 'REPOSITORY_BRANCH', value: params.releaseTag]]
        if (releaseBuildJob.getResult() == 'SUCCESS') {
            rintln "[SUCCESS] jdk${javaVersion} release downstream build jobs are created"
        }
    })
}
