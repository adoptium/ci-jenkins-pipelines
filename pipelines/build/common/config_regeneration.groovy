import common.IndividualBuildConfig
import common.RepoHandler
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Base64
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

/**
This file is a job that regenerates all of the build configurations in pipelines/build/jobs/configurations/jdk*_pipeline_config.groovy. This ensures that race conditions are not encountered when running concurrent pipeline builds.

1) Its called from jdk<version>_regeneration_pipeline.groovy
2) Attempts to create downstream job dsl's for each pipeline job configuration
*/
class Regeneration implements Serializable {
    private final String javaVersion
    private final Map<String, Map<String, ?>> buildConfigurations
    private final Map<String, ?> targetConfigurations
    private final Map<String, ?> DEFAULTS_JSON
    private final Map<String, ?> excludedBuilds
    private Integer sleepTime
    private final def currentBuild
    private final def context

    private final def jobRootDir
    private final def gitRemoteConfigs
    private final def gitBranch

    private final def jobTemplatePath
    private final def libraryPath
    private final def baseFilePath
    private final def scriptPath
    private final def jenkinsBuildRoot
    private final def jenkinsCreds
    private final def checkoutCreds
    private final Boolean prBuilder

    private String javaToBuild
    private final List<String> defaultTestList = ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.external']

    private final String EXCLUDED_CONST = "EXCLUDED"

    /*
    Constructor
    */
    public Regeneration(
        String javaVersion,
        Map<String, Map<String, ?>> buildConfigurations,
        Map<String, ?> targetConfigurations,
        Map<String, ?> DEFAULTS_JSON,
        Map<String, ?> excludedBuilds,
        Integer sleepTime,
        currentBuild,
        context,
        String jobRootDir,
        Map gitRemoteConfigs,
        String gitBranch,
        String jobTemplatePath,
        String libraryPath,
        String baseFilePath,
        String scriptPath,
        String jenkinsBuildRoot,
        String jenkinsCreds,
        String checkoutCreds,
        Boolean prBuilder
    ) {
        this.javaVersion = javaVersion
        this.buildConfigurations = buildConfigurations
        this.targetConfigurations = targetConfigurations
        this.DEFAULTS_JSON = DEFAULTS_JSON
        this.excludedBuilds = excludedBuilds
        this.sleepTime = sleepTime
        this.currentBuild = currentBuild
        this.context = context
        this.jobRootDir = jobRootDir
        this.gitRemoteConfigs = gitRemoteConfigs
        this.gitBranch = gitBranch
        this.jobTemplatePath = jobTemplatePath
        this.libraryPath = libraryPath
        this.baseFilePath = baseFilePath
        this.scriptPath = scriptPath
        this.jenkinsBuildRoot = jenkinsBuildRoot
        this.jenkinsCreds = jenkinsCreds
        this.checkoutCreds = checkoutCreds
        this.prBuilder = prBuilder
    }

    /*
    * Get configure args from jdk*_pipeline_config.groovy. Used when creating the IndividualBuildConfig.
    * @param configuration
    * @param variant
    */
    static String getConfigureArgs(Map<String, ?> configuration, String variant) {
        def configureArgs = ""

        if (configuration.containsKey('configureArgs')) {
            def configConfigureArgs
            if (isMap(configuration.configureArgs)) {
                configConfigureArgs = (configuration.configureArgs as Map<String, ?>).get(variant)
            } else {
                configConfigureArgs = configuration.configureArgs
            }

            if (configConfigureArgs != null) {
                configureArgs += configConfigureArgs
            }
        }
        return configureArgs
    }

    def getArchLabel(Map<String, ?> configuration, String variant) {
        // Default to arch
        def archLabelVal = configuration.arch

        // Workaround for cross compiled architectures
        if (configuration.containsKey("crossCompile")) {
            def configArchLabelVal

            if (isMap(configuration.crossCompile)) {
                configArchLabelVal = (configuration.crossCompile as Map<String, ?>).get(variant)
            } else {
                configArchLabelVal = configuration.crossCompile
            }

            if (configArchLabelVal != null) {
                archLabelVal = configArchLabelVal
            }
        }

        return archLabelVal
    }

    /*
    Retrieves the dockerImage attribute from the build configurations.
    This specifies the DockerHub org and image to pull or build in case we don't have one stored in this repository.
    If this isn't specified, the openjdk_build_pipeline.groovy will assume we are not building the jdk inside of a container.
    */
    def getDockerImage(Map<String, ?> configuration, String variant) {
        def dockerImageValue = ""
        if (configuration.containsKey("dockerImage")) {
            if (isMap(configuration.dockerImage)) {
                dockerImageValue = (configuration.dockerImage as Map<String, ?>).get(variant)
            } else {
                dockerImageValue = configuration.dockerImage
            }
        }
        return dockerImageValue
    }

    /*
    Retrieves the dockerFile attribute from the build configurations.
    This specifies the path of the dockerFile relative to this repository.
    If a dockerFile is not specified, the openjdk_build_pipeline.groovy will attempt to pull one from DockerHub.
    */
    def getDockerFile(Map<String, ?> configuration, String variant) {
        def dockerFileValue = ""
        if (configuration.containsKey("dockerFile")) {
            if (isMap(configuration.dockerFile)) {
                dockerFileValue = (configuration.dockerFile as Map<String, ?>).get(variant)
            } else {
                dockerFileValue = configuration.dockerFile
            }
        }
        return dockerFileValue
    }

    /*
    Retrieves the dockerNode attribute from the build configurations.
    This determines what the additional label will be if we are building the jdk in a docker container.
    Defaults to &&dockerBuild in openjdk_build_pipeline.groovy if it's not supplied in the build configuration.
    */
    def getDockerNode(Map<String, ?> configuration, String variant) {
        def dockerNodeValue = ""
        if (configuration.containsKey("dockerNode")) {
            if (isMap(configuration.dockerNode)) {
                dockerNodeValue = (configuration.dockerNode as Map<String, ?>).get(variant)
            } else {
                dockerNodeValue = configuration.dockerNode
            }
        }
        return dockerNodeValue
    }

    /*
    Retrieves the dockerRegistry attribute from the build configurations.
    This is used to pull dockerImage from a custom registry.
    If not specified, defaults to '' which will be DockerHub.
    */
    def getDockerRegistry(Map<String, ?> configuration, String variant) {
        def dockerRegistryValue = ""
        if (configuration.containsKey("dockerRegistry")) {
            if (isMap(configuration.dockerRegistry)) {
                dockerRegistryValue = (configuration.dockerRegistry as Map<String, ?>).get(variant)
            } else {
                dockerRegistryValue = configuration.dockerRegistry
            }
        }
        return dockerRegistryValue
    }

    /*
    Retrieves the dockerCredential attribute from the build configurations.
    If used, this will wrap the docker pull with a docker login.
    */
    def getDockerCredential(Map<String, ?> configuration, String variant) {
        def dockerCredentialValue = ""
        if (configuration.containsKey("dockerCredential")) {
            if (isMap(configuration.dockerCredential)) {
                dockerCredentialValue = (configuration.dockerCredential as Map<String, ?>).get(variant)
            } else {
                dockerCredentialValue = configuration.dockerCredential
            }
        }
        return dockerCredentialValue
    }

    /*
    Retrieves the platformSpecificConfigPath from the build configurations.
    This determines where the location of the operating system setup files are in comparison to the repository root. The param is formatted like this because we need to download and source the file from the bash scripts.
    */
    def getPlatformSpecificConfigPath(Map<String, ?> configuration) {
        def splitUserUrl = ((String)DEFAULTS_JSON['repository']['build_url']).minus(".git").split('/')
        // e.g. https://github.com/adoptium/temurin-build.git will produce adoptium/temurin-build
        String userOrgRepo = "${splitUserUrl[splitUserUrl.size() - 2]}/${splitUserUrl[splitUserUrl.size() - 1]}"

        // e.g. adoptium/temurin-build/master/build-farm/platform-specific-configurations
        def platformSpecificConfigPath = "${userOrgRepo}/${DEFAULTS_JSON['repository']['build_branch']}/${DEFAULTS_JSON['configDirectories']['platform']}"

        if (configuration.containsKey("platformSpecificConfigPath")) {
            // e.g. adoptium/temurin-build/master/build-farm/platform-specific-configurations.linux.sh
            platformSpecificConfigPath = "${userOrgRepo}/${DEFAULTS_JSON['repository']['build_branch']}/${configuration.platformSpecificConfigPath}"
        }
        return platformSpecificConfigPath
    }

    /**
    * Builds up a node param string that defines what nodes are eligible to run the given job. Used as a placeholder since the pipelines overwrite this.
    * @param configuration
    * @param variant
    * @return
    */
    def formAdditionalBuildNodeLabels(Map<String, ?> configuration, String variant) {
        def buildTag = "build"
        def labels = "${buildTag}"

        if (configuration.containsKey("additionalNodeLabels")) {
            def additionalNodeLabels

            if (isMap(configuration.additionalNodeLabels)) {
                additionalNodeLabels = (configuration.additionalNodeLabels as Map<String, ?>).get(variant)
            } else {
                additionalNodeLabels = configuration.additionalNodeLabels
            }

            if (additionalNodeLabels != null) {
                labels = "${additionalNodeLabels}&&${labels}"
            }
        }

        return labels
    }

    /**
    * Builds up additional test labels
    * @param configuration
    * @param variant
    * @return
    */
    def formAdditionalTestLabels(Map<String, ?> configuration, String variant) {
        def labels = ""

        if (configuration.containsKey("additionalTestLabels")) {
            def additionalTestLabels

            if (isMap(configuration.additionalTestLabels)) {
                additionalTestLabels = (configuration.additionalTestLabels as Map<String, ?>).get(variant)
            } else {
                additionalTestLabels = configuration.additionalTestLabels
            }

            if (additionalTestLabels != null) {
                labels = "${additionalTestLabels}"
            }
        }

        return labels
    }

    /*
    * Get build args from jdk*_pipeline_config.groovy. Used when creating the IndividualBuildConfig.
    * @param configuration
    * @param variant
    */
    String getBuildArgs(Map<String, ?> configuration, variant) {
        if (configuration.containsKey('buildArgs')) {
            if (isMap(configuration.buildArgs)) {
                Map<String, ?> buildArgs = configuration.buildArgs as Map<String, ?>
                if (buildArgs.containsKey(variant)) {
                    return buildArgs.get(variant)
                }
            } else {
                return configuration.buildArgs
            }
        }

        return ""
    }

    /*
    * Get the list of tests from jdk*_pipeline_config.groovy. Used when creating the IndividualBuildConfig. Used as a placeholder since the pipelines overwrite this.
    * @param configuration
    */
    List<String> getTestList(Map<String, ?> configuration) {
        List<String> testList = []
        if (configuration.containsKey("test") && configuration.get("test")) {
            if (isMap(configuration.test)) {
                testList = (configuration.test as Map).get("nightly") as List<String> // no need to check for release
            }
            testList = defaultTestList
        }
        testList.unique()
        return testList
    }
    /*
    * Get the list of tests to dynamically run  parallel builds from the build configurations. Used as a placeholder since the pipelines overwrite this
    * @param configuration
    */
    Map<String, ?> getDynamicParams() {
        List<String> testLists = DEFAULTS_JSON["testDetails"]["defaultDynamicParas"]["testLists"]
        List<String> numMachines = DEFAULTS_JSON["testDetails"]["defaultDynamicParas"]["numMachines"]
        return ["testLists": testLists, "numMachines": numMachines]
    }
    /*
    * Checks if the platform/arch/variant is in the EXCLUDES_LIST Parameter.
    * @param configuration
    * @param variant
    */
    def overridePlatform(Map<String, ?> configuration, String variant) {
        Boolean overridePlatform = false
        if (excludedBuilds == [:]) {
            return overridePlatform
        }

        String stringArch = configuration.arch as String
        String stringOs = configuration.os as String
        String estimatedKey = stringArch + stringOs.capitalize()

        if (excludedBuilds.containsKey(estimatedKey)) {

            if (excludedBuilds[estimatedKey].contains(variant)) {
                overridePlatform = true
            }

        }

        return overridePlatform
    }

    /*
    * Create IndividualBuildConfig for jobDsl. Used as a placeholder since the pipelines overwrite this.
    * @param platformConfig
    * @param variant
    * @param javaToBuild
    */
    IndividualBuildConfig buildConfiguration(Map<String, ?> platformConfig, String variant, String javaToBuild) {
        try {

            // Check if it's in the excludes list
            if (overridePlatform(platformConfig, variant)) {
                context.println "[INFO] Excluding $platformConfig.os: $variant from $javaToBuild regeneration due to it being in the EXCLUDES_LIST..."
                return EXCLUDED_CONST
            }

            def additionalNodeLabels = formAdditionalBuildNodeLabels(platformConfig, variant)

            def additionalTestLabels = formAdditionalTestLabels(platformConfig, variant)

            def archLabel = getArchLabel(platformConfig, variant)

            def dockerImage = getDockerImage(platformConfig, variant)

            def dockerFile = getDockerFile(platformConfig, variant)

            def dockerNode = getDockerNode(platformConfig, variant)

            def dockerRegistry = getDockerRegistry(platformConfig, variant)

            def dockerCredential = getDockerCredential(platformConfig, variant)

            def platformSpecificConfigPath = getPlatformSpecificConfigPath(platformConfig)

            def buildArgs = getBuildArgs(platformConfig, variant)

            def testList = getTestList(platformConfig)

            def dynamicList = getDynamicParams().get("testLists")

            def numMachines = getDynamicParams().get("numMachines")

           return new IndividualBuildConfig( // final build config
                JAVA_TO_BUILD: javaToBuild,
                ARCHITECTURE: platformConfig.arch as String,
                TARGET_OS: platformConfig.os as String,
                VARIANT: variant,
                TEST_LIST: testList,
                DYNAMIC_LIST: dynamicList,
                NUM_MACHINES: numMachines,
                SCM_REF: "",
                AQA_REF: "",
                AQA_AUTO_GEN: false,
                BUILD_ARGS: buildArgs,
                NODE_LABEL: "${additionalNodeLabels}&&${platformConfig.os}&&${archLabel}",
                ADDITIONAL_TEST_LABEL: "${additionalTestLabels}",
                KEEP_TEST_REPORTDIR: false,
                ACTIVE_NODE_TIMEOUT: "",
                CODEBUILD: platformConfig.codebuild as Boolean,
                DOCKER_IMAGE: dockerImage,
                DOCKER_FILE: dockerFile,
                DOCKER_NODE: dockerNode,
                DOCKER_REGISTRY: dockerRegistry,
                DOCKER_CREDENTIAL: dockerCredential,
                PLATFORM_CONFIG_LOCATION: platformSpecificConfigPath,
                CONFIGURE_ARGS: getConfigureArgs(platformConfig, variant),
                OVERRIDE_FILE_NAME_VERSION: "",
                USE_ADOPT_SHELL_SCRIPTS: true,
                ADDITIONAL_FILE_NAME_TAG: platformConfig.additionalFileNameTag as String,
                JDK_BOOT_VERSION: platformConfig.bootJDK as String,
                RELEASE: false,
                PUBLISH_NAME: "",
                ADOPT_BUILD_NUMBER: "",
                ENABLE_TESTS: DEFAULTS_JSON['testDetails']['enableTests'] as Boolean,
                ENABLE_TESTDYNAMICPARALLEL: DEFAULTS_JSON['testDetails']['enableTestDynamicParallel'] as Boolean,
                ENABLE_INSTALLERS: true,
                ENABLE_SIGNER: true,
                CLEAN_WORKSPACE: true,
                CLEAN_WORKSPACE_AFTER: true,
                CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER: false
            )
        } catch (Exception e) {
            throw new Exception("[ERROR] Failed to create IndividualBuildConfig for platformConfig: ${platformConfig}.\n${e}")
        }
    }

    /**
    * Checks if the parameter is a map
    * @param possibleMap
    */
    static def isMap(possibleMap) {
        return Map.class.isInstance(possibleMap)
    }

    /**
    * Generates a job from template at `create_job_from_template.groovy`. This is what creates the job dsl and "regenerates" the job.
    * @param jobName
    * @param jobFolder
    * @param config
    */
    def createJob(jobName, jobFolder, IndividualBuildConfig config) {
        Map<String, ?> params = config.toMap().clone() as Map
        params.put("JOB_NAME", jobName)
        params.put("JOB_FOLDER", jobFolder)
        params.put("VARIANT", config.VARIANT)
        params.put("SCRIPT_PATH", scriptPath)

        params.put("GIT_URL", gitRemoteConfigs['url'])
        params.put("GIT_BRANCH", gitBranch)

        // We have to use JsonSlurpers throughout the code for instantiating maps for consistancy and parsing reasons
        Map userRemoteConfigs = new JsonSlurper().parseText('{"branch" : "", "remotes": ""}') as Map
        userRemoteConfigs.branch = gitBranch
        userRemoteConfigs.remotes = gitRemoteConfigs
        params.put("USER_REMOTE_CONFIGS", JsonOutput.prettyPrint(JsonOutput.toJson(userRemoteConfigs)))

        def repoHandler = new RepoHandler(userRemoteConfigs)
        repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON)

        params.put("DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(DEFAULTS_JSON)))
        Map ADOPT_DEFAULTS_JSON = repoHandler.getAdoptDefaultsJson()
        params.put("ADOPT_DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(ADOPT_DEFAULTS_JSON)))

        params.put("BUILD_CONFIG", config.toJson())

        // If we are not using default lib or script param values, be sure to update the initial downstream job script file
        if (libraryPath != DEFAULTS_JSON['importLibraryScript']) {
            params.put("CUSTOM_LIBRARY_LOCATION", libraryPath)
        }
        if (baseFilePath != DEFAULTS_JSON["baseFileDirectories"]["downstream"]) {
            params.put("CUSTOM_BASEFILE_LOCATION", baseFilePath)
        }

        // Pass in checkout creds if needs be
        if (checkoutCreds != "") {
            params.put("CHECKOUT_CREDENTIALS", checkoutCreds)
        } else {
            params.put("CHECKOUT_CREDENTIALS", "")
        }

        // Make sure the dsl knows if we're building inside the pr tester
        if (prBuilder) {
            params.put("PR_BUILDER", true)
        }

        // Execute job dsl, using adopt's template if the user doesn't have one
        def create = null
        try {
            create = context.jobDsl targets: jobTemplatePath, ignoreExisting: false, additionalParameters: params
        } catch (Exception e) {
            context.println "[WARNING] Something went wrong when creating the job dsl. It may be because we are trying to pull the template inside a user repository. Using Adopt's template instead. Error:\n${e}"
            repoHandler.checkoutAdoptPipelines(context)
            create = context.jobDsl targets: ADOPT_DEFAULTS_JSON['templateDirectories']['downstream'], ignoreExisting: false, additionalParameters: params
            repoHandler.checkoutUserPipelines(context)
        }

        return create
    }

    /**
    * Make downstream job from config and name
    * @param jobConfig
    * @param jobName
    */
    def makeJob(def jobConfig, def jobName) {
        IndividualBuildConfig config = jobConfig.get(jobName)

        // jdk8u-linux-x64-hotspot
        def jobTopName = "${javaToBuild}-${jobName}"
        def jobFolder = "${jobRootDir}/jobs/${javaToBuild}"

        // i.e jdk8u/jobs/jdk8u-linux-x64-hotspot
        def downstreamJobName = "${jobFolder}/${jobTopName}"
        context.println "[INFO] build name: ${downstreamJobName}"

        // Job dsl
        createJob(jobTopName, jobFolder, config)

        // Job regenerated correctly
        context.println "[SUCCESS] Regenerated configuration for job $downstreamJobName\n"
    }

    /**
    * Queries an API. Used to get the pipeline details
    * @param query
    */
    def queryAPI(String query) {
        try {
            def getJenkins = new URL(query).openConnection()

            // Set request credentials if they exist
            if (jenkinsCreds != "") {
                def jenkinsAuth = "Basic " + new String(Base64.getEncoder().encode(jenkinsCreds.getBytes()))
                getJenkins.setRequestProperty ("Authorization", jenkinsAuth)
            }

            def response = new JsonSlurper().parseText(getJenkins.getInputStream().getText())
            return response
        } catch (Exception e) {
            // Failed to connect to jenkins api or a parsing error occurred
            throw new Exception("[ERROR] Failure on jenkins api connection or parsing.\n${e}")
        }
    }

    /**
    * Main function. Ran from build_job_generator.groovy, this will be what jenkins will run first.
    */
    @SuppressWarnings("unused")
    def regenerate() {
        context.timestamps {
            def versionNumbers = javaVersion =~ /\d+/

            /*
            * Stage: Check that the pipeline isn't in in-progress or queued up. Once clear, run the regeneration job
            */
            context.stage("Check $javaVersion pipeline status") {

                if (jobRootDir.contains("pr-tester")) {
                    // No need to check if we're going to overwrite anything for the PR tester since concurrency isn't enabled -> https://github.com/adoptium/temurin-build/pull/2155
                    context.println "[SUCCESS] Don't need to check if the pr-tester is running as concurrency is disabled. Running regeneration job..."
                } else {
                    // Get all pipelines
                    def getPipelines = queryAPI("${jenkinsBuildRoot}/api/json?tree=jobs[name]&pretty=true&depth1")

                    // Parse api response to only extract the relevant pipeline
                    getPipelines.jobs.name.each{ pipeline ->
                        if (pipeline == "openjdk${versionNumbers[0]}-pipeline") {

                            Boolean inProgress = true
                            while (inProgress) {
                                // Check if pipeline is in progress using api
                                context.println "[INFO] Checking if ${pipeline} is running..." //i.e. openjdk8-pipeline

                                def pipelineInProgress = queryAPI("${jenkinsBuildRoot}/job/${pipeline}/lastBuild/api/json?pretty=true&depth1")

                                // If query fails, check to see if the pipeline has been run before
                                if (pipelineInProgress == null) {
                                    def getPipelineBuilds = queryAPI("${jenkinsBuildRoot}/job/${pipeline}/api/json?pretty=true&depth1")

                                    if (getPipelineBuilds.builds == []) {
                                        context.println "[SUCCESS] ${pipeline} has not been run before. Running regeneration job..."
                                        inProgress = false
                                    }

                                } else {
                                    inProgress = pipelineInProgress.building as Boolean
                                }

                                if (inProgress) {
                                    // Null safety check sleep as sleeping null may cause jenkins DoS
                                    if (!sleepTime) {
                                        sleepTime = 900
                                    }
                                    // Sleep for a bit, then check again...
                                    context.println "[INFO] ${pipeline} is running. Sleeping for ${sleepTime} seconds while waiting for ${pipeline} to complete..."

                                    context.sleep(time: sleepTime, unit: "SECONDS")
                                }

                            }

                            context.println "[SUCCESS] ${pipeline} is idle. Running regeneration job..."
                        }

                    }

                }

            } // end check stage

            /*
            * Stage: Regenerate all of the job configurations by job type (i.e. jdk8u-linux-x64-hotspot
            * jdk8u-linux-x64-openj9, etc.)
            */
            context.stage("Regenerate $javaVersion pipeline jobs") {

                // If we're building jdk head, update the javaToBuild
                context.println "[INFO] Querying Adoptium api to get the JDK-Head number"

                def JobHelper = context.library(identifier: 'openjdk-jenkins-helper@master').JobHelper
                Integer jdkHeadNum = Integer.valueOf(JobHelper.getAvailableReleases(context).tip_version)

                if (Integer.valueOf(versionNumbers[0]) == jdkHeadNum) {
                    javaToBuild = "jdk"
                    context.println "[INFO] This IS JDK-HEAD. javaToBuild is ${javaToBuild}."
                } else {
                    javaToBuild = "${javaVersion}"
                    context.println "[INFO] This IS NOT JDK-HEAD. javaToBuild is ${javaToBuild}..."
                }

                // Regenerate each os and arch
                targetConfigurations.keySet().each { osarch ->

                    context.println "[INFO] Regenerating: $osarch"

                        for (def variant in targetConfigurations.get(osarch)) {

                            context.println "[INFO] Regenerating variant $osarch: $variant..."

                            // Construct configuration for downstream job
                            Map<String, IndividualBuildConfig> jobConfigurations = [:]
                            String name = null
                            Boolean keyFound = false

                            // Using a foreach here as containsKey doesn't work for some reason
                            buildConfigurations.keySet().each { key ->
                                if (key == osarch) {

                                    //For build type, generate a configuration
                                    context.println "[INFO] FOUND MATCH! buildConfiguration key: ${key} and config file key: ${osarch}"
                                    keyFound = true

                                    def platformConfig = buildConfigurations.get(key) as Map<String, ?>

                                    name = "${platformConfig.os}-${platformConfig.arch}-${variant}"

                                    if (platformConfig.containsKey('additionalFileNameTag')) {
                                        name += "-${platformConfig.additionalFileNameTag}"
                                    }

                                    jobConfigurations[name] = buildConfiguration(platformConfig, variant, javaToBuild)
                                }
                            }

                            if (keyFound == false) {
                                context.println "[WARNING] Config file key: ${osarch} not recognised. Valid configuration keys for ${javaToBuild} are ${buildConfigurations.keySet()}.\n[WARNING] ${osarch} WILL NOT BE REGENERATED! Setting build result to UNSTABLE..."
                                currentBuild.result = "UNSTABLE"
                            } else {
                                // Skip variant job make if it's marked as excluded
                                if (jobConfigurations.get(name) == EXCLUDED_CONST) {
                                    continue
                                }
                                // Make job
                                else if (jobConfigurations.get(name) != null) {
                                    makeJob(jobConfigurations, name)
                                // Unexpected error when building or getting the configuration
                                } else {
                                    throw new Exception("[ERROR] IndividualBuildConfig is malformed or null for key: ${osarch} : ${variant}.")
                                }
                            }

                        } // end variant for loop

                        context.println "[SUCCESS] ${osarch} completed!\n"

                } // end key foreach loop

            } // end stage
        } // end timestamps
    } // end regenerate()

}

return {
    String javaVersion,
    Map<String, Map<String, ?>> buildConfigurations,
    Map<String, ?> targetConfigurations,
    Map<String, ?> DEFAULTS_JSON,
    String excludes,
    Integer sleepTime,
    def currentBuild,
    def context,
    String jobRootDir,
    Map gitRemoteConfigs,
    String gitBranch,
    String jobTemplatePath,
    String libraryPath,
    String baseFilePath,
    String scriptPath,
    String jenkinsBuildRoot,
    String jenkinsCreds,
    String checkoutCreds,
    Boolean prBuilder
        ->

        def excludedBuilds = [:]
        if (excludes != "" && excludes != null) {
            excludedBuilds = new JsonSlurper().parseText(excludes) as Map
        }

        return new Regeneration(
            javaVersion,
            buildConfigurations,
            targetConfigurations,
            DEFAULTS_JSON,
            excludedBuilds,
            sleepTime,
            currentBuild,
            context,
            jobRootDir,
            gitRemoteConfigs,
            gitBranch,
            jobTemplatePath,
            libraryPath,
            baseFilePath,
            scriptPath,
            jenkinsBuildRoot,
            jenkinsCreds,
            checkoutCreds,
            prBuilder
        )
}
