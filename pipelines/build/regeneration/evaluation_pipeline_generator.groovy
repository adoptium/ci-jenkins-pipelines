import java.nio.file.NoSuchFileException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/* 
file used as jenkinsfile to generator evaluation and weekly-evaluation pipeline
*/

node('worker') {
    try {
        // Pull in Adopt defaults
        String ADOPT_DEFAULTS_FILE_URL = 'https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/master/pipelines/defaults.json'
        def getAdopt = new URL(ADOPT_DEFAULTS_FILE_URL).openConnection()
        Map<String, ?> ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
        if (!ADOPT_DEFAULTS_JSON || !Map.isInstance(ADOPT_DEFAULTS_JSON)) {
            throw new Exception("[ERROR] No ADOPT_DEFAULTS_JSON found at ${ADOPT_DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file. NOTE: Since this adopt's defaults and unlikely to change location, this is likely a network or GitHub issue.")
        }

        // Pull in User defaults
        String DEFAULTS_FILE_URL = (params.DEFAULTS_URL) ?: ADOPT_DEFAULTS_FILE_URL
        def getUser = new URL(DEFAULTS_FILE_URL).openConnection()
        Map<String, ?> DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
        if (!DEFAULTS_JSON || !Map.isInstance(DEFAULTS_JSON)) {
            throw new Exception("[ERROR] No DEFAULTS_JSON found at ${DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file.")
        }

        Map remoteConfigs = [:]
        def repoBranch = null

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

        timestamps {
            def validVersion = [8, 11, 17, 19, 20]
            def generatedPipelines = []

            // Load git url and branch and gitBranch. These determine where we will be pulling user configs from.
            def repoUri = (params.REPOSITORY_URL) ?: DEFAULTS_JSON['repository']['pipeline_url']
            repoBranch = (params.REPOSITORY_BRANCH) ?: DEFAULTS_JSON['repository']['pipeline_branch']

            // Load credentials to be used in checking out. This is in case we are checking out a URL that is not Adopts and they don't have their ssh key on the machine.
            def checkoutCreds = (params.CHECKOUT_CREDENTIALS) ?: ''
            remoteConfigs = [ url: repoUri ]
            if (checkoutCreds != '') {
                // NOTE: This currently does not work with user credentials due to https://issues.jenkins.io/browse/JENKINS-60349
                remoteConfigs.put('credentials', "${checkoutCreds}")
            } else {
                println "[WARNING] CHECKOUT_CREDENTIALS not specified! Checkout to $repoUri may fail if you do not have your ssh key on this machine."
            }

            // Checkout into user repository
            checkoutUserPipelines()

            String helperRef = DEFAULTS_JSON['repository']['helper_ref']
            library(identifier: "openjdk-jenkins-helper@${helperRef}")

            // Load jobRoot. This is where the openjdkxx-pipeline jobs will be created.
            def jobRoot = (params.JOB_ROOT) ?: DEFAULTS_JSON['jenkinsDetails']['rootDirectory']

            /*
            Load scriptFolderPath. This is the folder where the openjdk_pipeline.groovy code is located compared to the repository root.
            These are the top level pipeline jobs.
            */
            def scriptFolderPath = (params.SCRIPT_FOLDER_PATH) ?: DEFAULTS_JSON['scriptDirectories']['upstream']

            if (!fileExists(scriptFolderPath)) {
                println "[WARNING] ${scriptFolderPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
                checkoutAdoptPipelines()
                scriptFolderPath = ADOPT_DEFAULTS_JSON['scriptDirectories']['upstream']
                println "[SUCCESS] The path is now ${scriptFolderPath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
                checkoutUserPipelines()
            }

            /*
            Load evaluationFolderPath. This is the folder where the configurations/jdkxxu_pipeline_config.groovy code is located compared to the repository root.
            These define what the default set of evaluation will be.
            */
            def evaluationFolderPath = DEFAULTS_JSON['configDirectories']['evaluation']

            if (!fileExists(EvaluationFolderPath)) {
                println "[WARNING] ${EvaluationFolderPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
                checkoutAdoptPipelines()
                EvaluationFolderPath = ADOPT_DEFAULTS_JSON['configDirectories']['evaluation']
                println "[SUCCESS] The path is now ${EvaluationFolderPath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
                checkoutUserPipelines()
            }

            /*
            Load jobTemplatePath. This is where the pipeline_job_template.groovy code is located compared to the repository root.
            This actually sets up the pipeline job using the parameters above.
            */
            def jobTemplatePath = (params.JOB_TEMPLATE_PATH) ?: DEFAULTS_JSON['templateDirectories']['upstream']

            if (!fileExists(jobTemplatePath)) {
                println "[WARNING] ${jobTemplatePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
                checkoutAdoptPipelines()
                jobTemplatePath = ADOPT_DEFAULTS_JSON['templateDirectories']['upstream']
                println "[SUCCESS] The path is now ${jobTemplatePath} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
                checkoutUserPipelines()
            }

            // Load enablePipelineSchedule. This determines whether we will be generating the pipelines with a schedule (defined in jdkxx.groovy) or not.
            Boolean enablePipelineSchedule = false
            if (params.ENABLE_PIPELINE_SCHEDULE) {
                enablePipelineSchedule = true
            }

            // Load useAdoptShellScripts. This determines whether we will checkout to adopt's repository before running make-adopt-build-farm.sh or if we use the user's bash scripts.
            Boolean useAdoptShellScripts = false
            if (params.USE_ADOPT_SHELL_SCRIPTS) {
                useAdoptShellScripts = true
            }

            println '[INFO] Running generator script with the following configuration:'
            println "REPOSITORY_URL = $repoUri"
            println "REPOSITORY_BRANCH = $repoBranch"
            println "JOB_ROOT = $jobRoot"
            println "SCRIPT_FOLDER_PATH = $scriptFolderPath"
            println "EVALUATION_FOLDER_PATH = $evaluationFolderPath"
            println "JOB_TEMPLATE_PATH = $jobTemplatePath"
            println "ENABLE_PIPELINE_SCHEDULE = $enablePipelineSchedule"
            println "USE_ADOPT_SHELL_SCRIPTS = $useAdoptShellScripts"

            // Collect available JDK versions to check for generation (tip_version + 1 just in case it is out of date on a release day)
            def JobHelper = library(identifier: "openjdk-jenkins-helper@${helperRef}").JobHelper
            println 'Querying Adopt Api for the JDK-Head number (tip_version)...'

            def response = JobHelper.getAvailableReleases(this)
            int headVersion = (int) response[('tip_version')]

            (8..headVersion + 1).each({ javaVersion ->
                if (validVersion.contains(javaVersion)) {
                    
                    def config = [
                        TEST                : false,
                        GIT_URL             : repoUri,
                        BRANCH              : repoBranch,
                        BUILD_FOLDER        : jobRoot,
                        CHECKOUT_CREDENTIALS: checkoutCreds,
                        JAVA_VERSION        : javaVersion,
                        JOB_NAME            : "evaluation-openjdk${javaVersion}-pipeline",
                        SCRIPT              : "${scriptFolderPath}/openjdk_pipeline.groovy",
                        disableJob          : false,
                        pipelineSchedule    : '0 0 31 2 0', // 31st Feb, so will never run,
                        adoptScripts        : false,
                        releaseType         : 'Nightly Without Publish' // no need to set releaseType to "release" for prototoype pipeline
                    ]
                
                    /* logic of creating evaluation pipeline start*/

                    // read out different target
                    def targetEvaluation
                    try {
                        def uFile = "${WORKSPACE}/${EvaluationFolderPath}/jdk${javaVersion}u_evaluation.groovy"
                        def nonUFile = "${WORKSPACE}/${EvaluationFolderPath}/jdk${javaVersion}_evaluation.groovy"
                        if(fileExists(uFile)) {
                            targetEvaluation = load uFile
                        } else {
                            targetEvaluation = load nonUFile
                        }
                    } catch (NoSuchFileException e) {
                        checkoutAdoptPipelines
                        try {
                            def uFile2 = "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['evaluation']}/jdk${javaVersion}u_evaluation.groovy"
                            def nonUFile2 = "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['evaluation']}/jdk${javaVersion}_evaluation.groovy"
                            if(fileExists(uFile2)) {
                                targetEvaluation = load uFile2
                            } else {
                                targetEvaluation = load nonUFile2
                            }
                        } catch (NoSuchFileException e2) {
                            throw new Exception("[ERROR] enable to load jdk${javaVersion}u_evaluation.groovy nor jdk${javaVersion}_evaluation.groovy does not exist!")
                        }
                    }
                    config.put('targetConfigurations', targetEvaluation.targetConfigurations)

                    // if has a triggerSchedule_evaluation variable set then use it or default to '0 0 31 2 0'/never run
                    if (enablePipelineSchedule.toBoolean()){
                        config.put('pipelineSchedule', targetEvaluation.triggerSchedule_evaluation)
                    } else { // empty to not run
                        config.put('pipelineSchedule', '')
                    }

                    // hack as jenkins groovy does not seem to allow us to check if disableJob exists
                    try {
                        config.put('disableJob', targetEvaluation.disableJob)
                    } catch (Exception ex) {
                        config.put('disableJob', false)
                    }

                    if (useAdoptShellScripts.toBoolean()) {
                        config.put('adoptScripts', true)
                    }

                    config.put('enableTests', DEFAULTS_JSON['testDetails']['enableTests'] as Boolean)
                    config.put('enableTestDynamicParallel', DEFAULTS_JSON['testDetails']['enableTestDynamicParallel'] as Boolean)
                    config.put('defaultsJson', DEFAULTS_JSON)
                    config.put('adoptDefaultsJson', ADOPT_DEFAULTS_JSON)

                    // genereate pipeline
                    println "[INFO] FINAL CONFIG FOR EVALUATION JDK${javaVersion}"
                    println JsonOutput.prettyPrint(JsonOutput.toJson(config))
                    try {
                        jobDsl targets: jobTemplatePath, ignoreExisting: false, additionalParameters: config
                    } catch (Exception e) {
                        println "${e}\n[WARNING] Something went wrong when creating the job dsl. It may be because we are trying to pull the template inside a user repository. Using Adopt's template instead..."
                        checkoutAdoptPipelines()
                        jobDsl targets: ADOPT_DEFAULTS_JSON['templateDirectories']['upstream'], ignoreExisting: false, additionalParameters: config
                        checkoutUserPipelines()
                    }
                    
                    // add into list
                    generatedPipelines.add(config['JOB_NAME'])
    
                    /* logic of creating evaluation weekly pipeline start */
                    config.JOB_NAME = "weekly-evaluation-openjdk${javaVersion}-pipeline"
                    config.SCRIPT = (params.WEEKLY_SCRIPT_PATH) ?: DEFAULTS_JSON['scriptDirectories']['weekly']
                    if (!fileExists(config.SCRIPT)) {
                        println "[WARNING] ${config.SCRIPT} does not exist, next to checkout Adopt's weekly"
                        checkoutAdoptPipelines()
                        config.SCRIPT = ADOPT_DEFAULTS_JSON['scriptDirectories']['weekly']
                        println "[SUCCESS] The path is now ${config.SCRIPT} relative to ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']}"
                        checkoutUserPipelines()
                    }
                    config.PIPELINE = "evaluation-openjdk${javaVersion}-pipeline"
                    if (enablePipelineSchedule.toBoolean()) {
                        config.put('pipelineSchedule', targetEvaluation.triggerSchedule_weekly_evaluation)
                    } else { // empty string will never run
                        config.put('pipelineSchedule', '')
                    }
                    config.put('targetConfigurations', targetEvaluation.targetConfigurations) // explicit set it to make things clear
                    config.weekly_release_scmReferences = targetEvaluation.weekly_evaluation_scmReferences

                    println "[INFO] CREATING JDK${javaVersion} WEEKLY EVALUATION PIPELINE WITH NEW CONFIG VALUES:"
                    println "JOB_NAME = ${config.JOB_NAME}"
                    println "SCRIPT = ${config.SCRIPT}"
                    println "PIPELINE = ${config.PIPELINE}"
                    println "releaseType = ${config.releaseType}"
                    println "targetConfigurations = ${config.targetConfigurations}"
                    println "weekly_release_scmReferences = ${config.weekly_release_scmReferences}"

                    // genereate pipeline
                    // Load weeklyTemplatePath.
                    def weeklyTemplatePath = (params.WEEKLY_TEMPLATE_PATH) ?: DEFAULTS_JSON['templateDirectories']['weekly']

                    try {
                        jobDsl targets: weeklyTemplatePath, ignoreExisting: false, additionalParameters: config
                    } catch (Exception e) {
                        println "${e}\n[WARNING] Something went wrong when creating the weekly evaluation job dsl. It may be because we are trying to pull the template inside a user repository. Using Adopt's template instead..."
                        checkoutAdoptPipelines()
                        jobDsl targets: ADOPT_DEFAULTS_JSON['templateDirectories']['weeklyTemplatePath'], ignoreExisting: false, additionalParameters: config
                        checkoutUserPipelines()
                    }
                    // add into list
                    generatedPipelines.add(config['JOB_NAME'])
                }
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