import common.IndividualBuildConfig
import groovy.json.*

import java.util.regex.Matcher
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

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
 * Represents parameters that get past to each individual build
 */
/**
 * This file starts a high level job, it is called from openjdk_pipeline.groovy.
 *
 * This:
 *
 * 1. Generate job for each configuration based on create_job_from_template.groovy
 * 2. Execute job
 * 3. Push generated artifacts to github
 */
//@CompileStatic(extensions = "JenkinsTypeCheckHelperExtension")
class Builder implements Serializable {
    String javaToBuild
    Map<String, Map<String, ?>> buildConfigurations
    Map<String, List<String>> targetConfigurations
    Map<String, ?> DEFAULTS_JSON
    String activeNodeTimeout
    Map<String, List<String>> dockerExcludes
    boolean enableTests
    boolean enableTestDynamicParallel
    boolean enableInstallers
    boolean enableSigner
    boolean publish
    boolean release
    String releaseType
    String scmReference
    String aqaReference
    boolean aqaAutoGen
    String publishName
    String additionalConfigureArgs
    def scmVars
    String additionalBuildArgs
    String overrideFileNameVersion
    String adoptBuildNumber
    boolean useAdoptShellScripts
    boolean cleanWorkspaceBeforeBuild
    boolean cleanWorkspaceAfterBuild
    boolean cleanWorkspaceBuildOutputAfterBuild
    boolean propagateFailures
    boolean keepTestReportDir
    boolean keepReleaseLogs

    def currentBuild
    def context
    def env

    // Declare timeouts for each critical stage (unit is HOURS)
    Map pipelineTimeouts = [
        API_REQUEST_TIMEOUT : 1,
        REMOVE_ARTIFACTS_TIMEOUT : 2,
        COPY_ARTIFACTS_TIMEOUT : 6,
        ARCHIVE_ARTIFACTS_TIMEOUT : 6,
        PUBLISH_ARTIFACTS_TIMEOUT : 3
    ]

    /*
    Returns an IndividualBuildConfig that is passed down to the downstream job.
    It uses several helper functions to pull in and parse the build configuration for the job.
    This overrides the default IndividualBuildConfig generated in config_regeneration.groovy.
    */
    IndividualBuildConfig buildConfiguration(Map<String, ?> platformConfig, String variant) {

        // Query the Adopt api to get the "tip_version"
        def JobHelper = context.library(identifier: 'openjdk-jenkins-helper@master').JobHelper
        context.println "Querying Adopt Api for the JDK-Head number (tip_version)..."
        def response = JobHelper.getAvailableReleases(context)
        int headVersion = (int) response.getAt("tip_version")
        context.println "Found Java Version Number: ${headVersion}"

        if (javaToBuild == "jdk${headVersion}") {
            javaToBuild = "jdk"
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

        if (additionalBuildArgs) {
            buildArgs += " " + additionalBuildArgs
        }

        def testList = getTestList(platformConfig, variant)

        def dynamicTestsParameters = getDynamicParams(platformConfig, variant)
        def dynamicList = dynamicTestsParameters.get("testLists")
        def numMachines = dynamicTestsParameters.get("numMachines")

        def platformCleanWorkspaceAfterBuild = getCleanWorkspaceAfterBuild(platformConfig)

        // Always clean on mac due to https://github.com/adoptium/temurin-build/issues/1980
        def cleanWorkspace = cleanWorkspaceBeforeBuild
        if (platformConfig.os == "mac") {
            cleanWorkspace = true
        }

        def cleanWsAfter = cleanWorkspaceAfterBuild
        if (platformCleanWorkspaceAfterBuild) {
            // Platform override specified
            cleanWsAfter = platformCleanWorkspaceAfterBuild
        }

        return new IndividualBuildConfig(
            JAVA_TO_BUILD: javaToBuild,
            ARCHITECTURE: platformConfig.arch as String,
            TARGET_OS: platformConfig.os as String,
            VARIANT: variant,
            TEST_LIST: testList,
            DYNAMIC_LIST: dynamicList,
            NUM_MACHINES: numMachines,
            SCM_REF: scmReference,
            AQA_REF: aqaReference,
            AQA_AUTO_GEN: aqaAutoGen,
            BUILD_ARGS: buildArgs,
            NODE_LABEL: "${additionalNodeLabels}&&${platformConfig.os}&&${archLabel}",
            ADDITIONAL_TEST_LABEL: "${additionalTestLabels}",
            KEEP_TEST_REPORTDIR: keepTestReportDir,
            ACTIVE_NODE_TIMEOUT: activeNodeTimeout,
            CODEBUILD: platformConfig.codebuild as Boolean,
            DOCKER_IMAGE: dockerImage,
            DOCKER_FILE: dockerFile,
            DOCKER_NODE: dockerNode,
            DOCKER_REGISTRY: dockerRegistry,
            DOCKER_CREDENTIAL: dockerCredential,
            PLATFORM_CONFIG_LOCATION: platformSpecificConfigPath,
            CONFIGURE_ARGS: getConfigureArgs(platformConfig, additionalConfigureArgs, variant),
            OVERRIDE_FILE_NAME_VERSION: overrideFileNameVersion,
            USE_ADOPT_SHELL_SCRIPTS: useAdoptShellScripts,
            ADDITIONAL_FILE_NAME_TAG: platformConfig.additionalFileNameTag as String,
            JDK_BOOT_VERSION: platformConfig.bootJDK as String,
            RELEASE: release,
            PUBLISH_NAME: publishName,
            ADOPT_BUILD_NUMBER: adoptBuildNumber,
            ENABLE_TESTS: enableTests,
            ENABLE_TESTDYNAMICPARALLEL: enableTestDynamicParallel,
            ENABLE_INSTALLERS: enableInstallers,
            ENABLE_SIGNER: enableSigner,
            CLEAN_WORKSPACE: cleanWorkspace,
            CLEAN_WORKSPACE_AFTER: cleanWsAfter,
            CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER: cleanWorkspaceBuildOutputAfterBuild
        )
    }

    /*
    Returns true if possibleMap is a Map. False otherwise.
    */
    static def isMap(possibleMap) {
        return Map.class.isInstance(possibleMap)
    }


    /*
    Retrieves the buildArgs attribute from the build configurations.
    These eventually get passed to ./makejdk-any-platform.sh and make images.
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
    Get the list of tests to run from the build configurations.
    We run different test categories depending on if this build is a release or nightly. This function parses and applies this to the individual build config.
    */
    List<String> getTestList(Map<String, ?> configuration, String variant) {
        final List<String> nightly = DEFAULTS_JSON["testDetails"]["nightlyDefault"]
        final List<String> weekly = DEFAULTS_JSON["testDetails"]["weeklyDefault"]
        List<String> testList = []
        /*
        * No test key or key value is test: false  --- test disabled
        * Key value is test: 'default' --- nightly build trigger 'nightly' test set, weekly build trigger or release build trigger 'nightly' + 'weekly' test sets
        * Key value is test: [customized map] specified nightly and weekly test lists
        * Key value is test: [customized map] specified for different variant
        */
        if (configuration.containsKey("test") && configuration.get("test")) {
            def testJobType = "nightly"
            if (releaseType.equals("Weekly") || releaseType.equals("Release")) {
                testJobType = "weekly"
            }
            if (isMap(configuration.test)) {
                if (configuration.test.containsKey(variant)) {
                    //Test is enable for the variant
                    if (configuration.test.get(variant)) {
                        def testObj = configuration.test.get(variant)
                        if (isMap(testObj)) {
                            if ( testJobType == "nightly" ) {
                                testList = (configuration.test.get(variant) as Map).get("nightly") as List<String>
                            } else {
                                testList = ((configuration.test.get(variant) as Map).get("nightly") as List<String>) + ((configuration.test as Map).get("weekly") as List<String>)
                            }
                        } else if (testObj instanceof List) {
                            testList = (configuration.test as Map).get(variant) as List<String>
                        } else {
                            if ( testJobType == "nightly" ) {
                                testList = nightly
                            } else {
                                testList = nightly + weekly
                            }
                        }
                    }
                } else {
                    if ( testJobType == "nightly" ) {
                        testList = (configuration.test as Map).get("nightly") as List<String>
                    } else {
                        testList = ((configuration.test as Map).get("nightly") as List<String>) + ((configuration.test as Map).get("weekly") as List<String>)
                    }
                }
            } else {

                // Default to the test sets declared if one isn't set in the build configuration
                if ( testJobType == "nightly" ) {
                    testList = nightly
                } else {
                    testList = nightly + weekly
                }

            }
        }

        testList.unique()

        return testList
    }
    /*
    Get the list of tests to dynamically run parallel builds from the build configurations.
    This function parses and applies this to the individual build config.
    */
    Map<String, ?> getDynamicParams(Map<String, ?> configuration, String variant) {
        List<String> testLists = []
        List<String> numMachines = []

        def testDynamicMap

        if (configuration.containsKey("testDynamic")) {
            // fetch from buildConfigurations for target

            // testDynamic could be map, list or boolean
            if (configuration.containsKey("testDynamic") && configuration.get("testDynamic")) {
                // fetch variant options
                testDynamicMap = configuration.get("testDynamic").get(variant)
            } else {
                // fetch generic options
                testDynamicMap = configuration.get("testDynamic")
            }
        } else if (DEFAULTS_JSON["testDetails"]["defaultDynamicParas"]) {
            // fetch default options
            testDynamicMap = DEFAULTS_JSON["testDetails"]["defaultDynamicParas"]
        }

        if (testDynamicMap) {
            if (testDynamicMap.containsKey("testLists")) {
                testLists.addAll(testDynamicMap.get("testLists"))
            }

            if (testDynamicMap.containsKey("numMachines")) {
                // populate the list of number of machines per tests
                if (List.class.isInstance(testDynamicMap.get("numMachines"))) {
                    // the size of the numMachines List should match the testLists size
                    // otherwize throw an error
                    // e.g. 
                    // testLists    = ['extended.openjdk', 'extended.jck', 'special.jck']
                    // numMachines  = ['3',                '2',            '5']

                    numMachines.addAll(testDynamicMap.get("numMachines"))

                    if (numMachines.size() < testLists.size()) {
                        throw new Exception("Configuration error for dynamic testing: missmatch between dymanic parallel test targets testListing: ${testListing} and numMachines: ${numMachines}")
                    }
                } else {
                    if (!testLists.isEmpty()) {
                        // work-around for numMachines as a String
                        // populate the List<String> number of machines with duplicates
                        numMachines.addAll(Collections.nCopies(testLists.size(), "${testDynamicMap.get('numMachines')}"))
                    }
                }
            }
        }

        return ["testLists": testLists, "numMachines": numMachines]
    }
    /*
    Get the cleanWorkspaceAfterBuild override for this platform configuration
    */
    Boolean getCleanWorkspaceAfterBuild(Map<String, ?> configuration) {
        Boolean cleanWorkspaceAfterBuild = null
        if (configuration.containsKey("cleanWorkspaceAfterBuild") && configuration.get("cleanWorkspaceAfterBuild")) {
            cleanWorkspaceAfterBuild = configuration.cleanWorkspaceAfterBuild as Boolean
        }

        return cleanWorkspaceAfterBuild
    }

    /*
    Parses and applies the dockerExcludes parameter.
    Any platforms/variants that are declared in this parameter are marked as excluded from docker building via this function. Even if they have a docker image or file declared in the build configurations!
    */
    def dockerOverride(Map<String, ?> configuration, String variant) {
        Boolean overrideDocker = false
        if (dockerExcludes == {}) {
            return overrideDocker
        }

        String stringArch = configuration.arch as String
        String stringOs = configuration.os as String
        String estimatedKey = stringArch + stringOs.capitalize()

        if (dockerExcludes.containsKey(estimatedKey)) {

            if (dockerExcludes[estimatedKey].contains(variant)) {
                overrideDocker = true
            }

        }

        return overrideDocker
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

        if (configuration.containsKey("dockerImage") && !dockerOverride(configuration, variant)) {
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

        if (configuration.containsKey("dockerFile") && !dockerOverride(configuration, variant)) {
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

    /*
    Constructs any necessary additional build labels from the build configurations.
    This builds up a node param string that defines what nodes are eligible to run the given job.
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
    Retrieves the configureArgs attribute from the build configurations.
    These eventually get passed to ./makejdk-any-platform.sh and bash configure.
    */
    static String getConfigureArgs(Map<String, ?> configuration, String additionalConfigureArgs, String variant) {
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

        if (additionalConfigureArgs) {
            configureArgs += " " + additionalConfigureArgs
        }

        return configureArgs
    }

    /*
    Imports the build configurations for the target version based off its key and variant.
    E.g. { "x64Linux" : [ "hotspot", "openj9" ] }
    */
    Map<String, IndividualBuildConfig> getJobConfigurations() {
        Map<String, IndividualBuildConfig> jobConfigurations = [:]

        //Parse nightly config passed to jenkins job
        targetConfigurations
                .each { target ->

                    //For each requested build type, generate a configuration
                    if (buildConfigurations.containsKey(target.key)) {
                        def platformConfig = buildConfigurations.get(target.key) as Map<String, ?>

                        target.value.each { variant ->
                            // Construct a rough job name from the build config and variant
                            String name = "${platformConfig.os}-${platformConfig.arch}-${variant}"

                            if (platformConfig.containsKey('additionalFileNameTag')) {
                                name += "-${platformConfig.additionalFileNameTag}"
                            }

                            // Fill in the name's value with an IndividualBuildConfig
                            jobConfigurations[name] = buildConfiguration(platformConfig, variant)
                        }
                    }
                }

        return jobConfigurations
    }

    /*
    Returns the JDK head number from the Adopt API
    */
    Integer getHeadVersionNumber() {
        try {
            context.timeout(time: pipelineTimeouts.API_REQUEST_TIMEOUT, unit: "HOURS") {
                // Query the Adopt api to get the "tip_version"
                def JobHelper = context.library(identifier: 'openjdk-jenkins-helper@master').JobHelper
                context.println "Querying Adopt Api for the JDK-Head number (tip_version)..."
                def response = JobHelper.getAvailableReleases(context)
                return (int) response.getAt("tip_version")
            }
        } catch (FlowInterruptedException e) {
            throw new Exception("[ERROR] Adopt API Request timeout (${pipelineTimeouts.API_REQUEST_TIMEOUT} HOURS) has been reached. Exiting...")
        }
    }

    /*
    Returns the java version number for this pipeline (e.g. 8, 11, 15, 16)
    */
    Integer getJavaVersionNumber() {
        // version should be something like "jdk8u" or "jdk" for HEAD
        Matcher matcher = javaToBuild =~ /.*?(?<version>\d+).*?/
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group('version'))
        } else if ("jdk".equalsIgnoreCase(javaToBuild.trim())) {
            return getHeadVersionNumber()
        } else {
            throw new Exception("Failed to read java version '${javaToBuild}'")
        }
    }


    /*
    Returns the release tool version string to use in the release job
    */
    def determineReleaseToolRepoVersion() {
        def number = getJavaVersionNumber()

        return "jdk${number}"
    }

    /*
    Returns the job name of the target downstream job
    */
    def getJobName(displayName) {
        return "${javaToBuild}-${displayName}"
    }

    /*
    Returns the jenkins folder of where it's assumed the downstream build jobs have been regenerated
    */
    def getJobFolder() {
        def parentDir = currentBuild.fullProjectName.substring(0, currentBuild.fullProjectName.lastIndexOf("/"))
        return parentDir + "/jobs/" + javaToBuild
    }

    /*
    Ensures that we don't release multiple variants at the same time
    Unless this is the weekend weekly release build that won't have a publishName
    */
    def checkConfigIsSane(Map<String, IndividualBuildConfig> jobConfigurations) {

        if (release && publishName) {

            // Doing a release
            def variants = jobConfigurations
                    .values()
                    .collect({ it.VARIANT })
                    .unique()

            if (variants.size() > 1) {
                context.error('Trying to release multiple variants at the same time, this is unusual')
                return false
            }
        }

        return true
    }

    /*
    Call job to push artifacts to github. Usually it's only executed on a nightly build
    */
    def publishBinary() {
        if (release) {
            // make sure to skip on release
            context.println("Not publishing release")
            return
        }

        def timestamp = new Date().format("yyyy-MM-dd-HH-mm", TimeZone.getTimeZone("UTC"))
        def tag = "${javaToBuild}-${timestamp}"

        if (publishName) {
            tag = publishName
        }

        context.stage("publish") {
            context.build job: 'build-scripts/release/refactor_openjdk_release_tool',
                    parameters: [
                        ['$class': 'BooleanParameterValue', name: 'RELEASE', value: release],
                        context.string(name: 'TAG', value: tag),
                        context.string(name: 'TIMESTAMP', value: timestamp),
                        context.string(name: 'UPSTREAM_JOB_NAME', value: env.JOB_NAME),
                        context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${currentBuild.getNumber()}"),
                        context.string(name: 'VERSION', value: determineReleaseToolRepoVersion())
                    ]
        }
    }

    /*
    Main function. This is what is executed remotely via the openjdkxx-pipeline and pr tester jobs
    */
    @SuppressWarnings("unused")
    def doBuild() {
        context.timestamps {
            Map<String, IndividualBuildConfig> jobConfigurations = getJobConfigurations()

            if (!checkConfigIsSane(jobConfigurations)) {
                return
            }

            if (release) {
                if (publishName) {
                    // Keep Jenkins release logs for real releases
                    currentBuild.setKeepLog(keepReleaseLogs)
                    currentBuild.setDisplayName(publishName)
                }
            }

            def jobs = [:]

            // Special case for JDK head where the jobs are called jdk-os-arch-variant
            if (javaToBuild == "jdk${getHeadVersionNumber()}") {
                javaToBuild = "jdk"
            }

            context.echo "Java: ${javaToBuild}"
            context.echo "OS: ${targetConfigurations}"
            context.echo "Enable tests: ${enableTests}"
            context.echo "Enable Installers: ${enableInstallers}"
            context.echo "Enable Signer: ${enableSigner}"
            context.echo "Use Adopt's Scripts: ${useAdoptShellScripts}"
            context.echo "Publish: ${publish}"
            context.echo "Release: ${release}"
            context.echo "Tag/Branch name: ${scmReference}"
            context.echo "AQA tests Release/Branch name: ${aqaReference}"
            context.echo "Force auto generate AQA test jobs: ${aqaAutoGen}"
            context.echo "Keep test reportdir: ${keepTestReportDir}"
            context.echo "Keep release logs: ${keepReleaseLogs}"

            jobConfigurations.each { configuration ->
                jobs[configuration.key] = {
                    IndividualBuildConfig config = configuration.value

                    // jdk11u-linux-x64-hotspot
                    def jobTopName = getJobName(configuration.key)
                    def jobFolder = getJobFolder()

                    // i.e jdk11u/job/jdk11u-linux-x64-hotspot
                    def downstreamJobName = "${jobFolder}/${jobTopName}"
                    context.echo "build name " + downstreamJobName

                    context.catchError {
                        // Execute build job for configuration i.e jdk11u/job/jdk11u-linux-x64-hotspot
                        context.stage(configuration.key) {
                            context.echo "Created job " + downstreamJobName

                            // execute build
                            def downstreamJob = context.build job: downstreamJobName, propagate: false, parameters: config.toBuildParams()

                            if (downstreamJob.getResult() == 'SUCCESS') {
                                // copy artifacts from build
                                context.println "[NODE SHIFT] MOVING INTO MASTER NODE..."
                                context.node("master") {
                                    context.catchError {

                                        //Remove the previous artifacts
                                        try {
                                            context.timeout(time: pipelineTimeouts.REMOVE_ARTIFACTS_TIMEOUT, unit: "HOURS") {
                                                context.sh "rm target/${config.TARGET_OS}/${config.ARCHITECTURE}/${config.VARIANT}/* || true"
                                            }
                                        } catch (FlowInterruptedException e) {
                                            throw new Exception("[ERROR] Previous artifact removal timeout (${pipelineTimeouts.REMOVE_ARTIFACTS_TIMEOUT} HOURS) for ${downstreamJobName} has been reached. Exiting...")
                                        }

                                        try {
                                            context.timeout(time: pipelineTimeouts.COPY_ARTIFACTS_TIMEOUT, unit: "HOURS") {
                                                context.copyArtifacts(
                                                        projectName: downstreamJobName,
                                                        selector: context.specific("${downstreamJob.getNumber()}"),
                                                        filter: 'workspace/target/*',
                                                        fingerprintArtifacts: true,
                                                        target: "target/${config.TARGET_OS}/${config.ARCHITECTURE}/${config.VARIANT}/",
                                                        flatten: true
                                                )
                                            }
                                        } catch (FlowInterruptedException e) {
                                            throw new Exception("[ERROR] Copy artifact timeout (${pipelineTimeouts.COPY_ARTIFACTS_TIMEOUT} HOURS) for ${downstreamJobName} has been reached. Exiting...")
                                        }

                                        // Checksum
                                        context.sh 'for file in $(ls target/*/*/*/*.tar.gz target/*/*/*/*.zip); do sha256sum "$file" > $file.sha256.txt ; done'

                                        // Archive in Jenkins
                                        try {
                                            context.timeout(time: pipelineTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT, unit: "HOURS") {
                                                context.archiveArtifacts artifacts: "target/${config.TARGET_OS}/${config.ARCHITECTURE}/${config.VARIANT}/*"
                                            }
                                        } catch (FlowInterruptedException e) {
                                            throw new Exception("[ERROR] Archive artifact timeout (${pipelineTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT} HOURS) for ${downstreamJobName}has been reached. Exiting...")
                                        }

                                    }
                                }
                                context.println "[NODE SHIFT] OUT OF MASTER NODE!"
                            } else if (propagateFailures) {
                                context.error("Build failed due to downstream failure of ${downstreamJobName}")
                                currentBuild.result = "FAILURE"
                            }
                        }
                    }
                }
            }
            context.parallel jobs

            // publish to github if needed
            // Dont publish release automatically
            if (publish && !release) {
                //During testing just remove the publish
                try {
                    context.timeout(time: pipelineTimeouts.PUBLISH_ARTIFACTS_TIMEOUT, unit: "HOURS") {
                        publishBinary()
                    }
                } catch (FlowInterruptedException e) {
                    throw new Exception("[ERROR] Publish binary timeout (${pipelineTimeouts.PUBLISH_ARTIFACTS_TIMEOUT} HOURS) has been reached OR the downstream publish job failed. Exiting...")
                }
            } else if (publish && release) {
                context.println "NOT PUBLISHING RELEASE AUTOMATICALLY"
            }

        }
    }
}

return {
    String javaToBuild,
    Map<String, Map<String, ?>> buildConfigurations,
    String targetConfigurations,
    Map<String, ?> DEFAULTS_JSON,
    String activeNodeTimeout,
    String dockerExcludes,
    String enableTests,
    String enableTestDynamicParallel,
    String enableInstallers,
    String enableSigner,
    String releaseType,
    String scmReference,
    String aqaReference,
    String aqaAutoGen,
    String overridePublishName,
    String useAdoptShellScripts,
    String additionalConfigureArgs,
    def scmVars,
    String additionalBuildArgs,
    String overrideFileNameVersion,
    String cleanWorkspaceBeforeBuild,
    String cleanWorkspaceAfterBuild,
    String cleanWorkspaceBuildOutputAfterBuild,
    String adoptBuildNumber,
    String propagateFailures,
    String keepTestReportDir,
    String keepReleaseLogs,
    def currentBuild,
    def context,
    def env ->

        boolean release = false
        if (releaseType == 'Release') {
            release = true
        }

        boolean publish = false
        if (releaseType == 'Nightly' || releaseType == 'Weekly') {
            publish = true
        }

        String publishName = '' // This is set to a timestamp later on if undefined
        if (overridePublishName) {
            publishName = overridePublishName
        } else if (release) {
            // Default to scmReference, remove any trailing "_adopt" from the tag if present
            if (scmReference) {
                publishName = scmReference.minus("_adopt")
            }
        }

        def buildsExcludeDocker = [:]
        if (dockerExcludes != "" && dockerExcludes != null) {
            buildsExcludeDocker = new JsonSlurper().parseText(dockerExcludes) as Map
        }

        return new Builder(
            javaToBuild: javaToBuild,
            buildConfigurations: buildConfigurations,
            targetConfigurations: new JsonSlurper().parseText(targetConfigurations) as Map,
            DEFAULTS_JSON: DEFAULTS_JSON,
            activeNodeTimeout: activeNodeTimeout,
            dockerExcludes: buildsExcludeDocker,
            enableTests: Boolean.parseBoolean(enableTests),
            enableTestDynamicParallel: Boolean.parseBoolean(enableTestDynamicParallel),
            enableInstallers: Boolean.parseBoolean(enableInstallers),
            enableSigner: Boolean.parseBoolean(enableSigner),
            publish: publish,
            release: release,
            releaseType: releaseType,
            scmReference: scmReference,
            aqaReference: aqaReference,
            aqaAutoGen: Boolean.parseBoolean(aqaAutoGen),
            publishName: publishName,
            additionalConfigureArgs: additionalConfigureArgs,
            scmVars: scmVars,
            additionalBuildArgs: additionalBuildArgs,
            overrideFileNameVersion: overrideFileNameVersion,
            useAdoptShellScripts: Boolean.parseBoolean(useAdoptShellScripts),
            cleanWorkspaceBeforeBuild: Boolean.parseBoolean(cleanWorkspaceBeforeBuild),
            cleanWorkspaceAfterBuild: Boolean.parseBoolean(cleanWorkspaceAfterBuild),
            cleanWorkspaceBuildOutputAfterBuild: Boolean.parseBoolean(cleanWorkspaceBuildOutputAfterBuild),
            adoptBuildNumber: adoptBuildNumber,
            propagateFailures: Boolean.parseBoolean(propagateFailures),
            keepTestReportDir: Boolean.parseBoolean(keepTestReportDir),
            keepReleaseLogs: Boolean.parseBoolean(keepReleaseLogs),
            currentBuild: currentBuild,
            context: context,
            env: env
        )

}
