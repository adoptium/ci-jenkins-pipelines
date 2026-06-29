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

/* groovylint-disable MethodCount */

import common.IndividualBuildConfig
import common.MetaData
import common.VersionInfo
import common.RepoHandler
import groovy.json.*
import java.nio.file.NoSuchFileException
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import java.util.regex.Matcher

/**
 * This file is a template for running a build for a given configuration
 * A configuration is for example jdk10u-mac-x64-temurin.
 *
 * This file is referenced by the pipeline template create_job_from_template.groovy
 *
 * A pipeline looks like:
 *  1. Check out and build JDK by calling build-farm/make-adopt-build-farm.sh
 *  2. Archive artifacts created by build
 *  3. Run all tests defined in the configuration
 *  4. Sign artifacts if needed and re-archive
 *
 */

/*
    Extracts the named regex element `groupName` from the `matched` regex matcher and adds it to `map.name`
    If it is not present add `0`
 */

class Build {

    final IndividualBuildConfig buildConfig
    final Map USER_REMOTE_CONFIGS
    final Map DEFAULTS_JSON
    final Map ADOPT_DEFAULTS_JSON
    final context
    final env
    final currentBuild

    VersionInfo versionInfo = null
    String scmRef = ''
    String fullVersionOutput = ''
    String makejdkArgs = ''
    String configureArguments = ''
    String makeCommandArgs = ''
    String j9Major = ''
    String j9Minor = ''
    String j9Security = ''
    String j9Tags = ''
    String vendorName = ''
    String buildSource = ''
    String openjdkSource = ''
    String openjdk_built_config = ''
    String dockerImageDigest = ''
    Map<String,String> dependency_version = new HashMap<String,String>()
    String crossCompileVersionPath = ''
    Map variantVersion = [:]

    // Declare timeouts for each critical stage (unit is HOURS)
    Map buildTimeouts = [
        API_REQUEST_TIMEOUT : 1,
        NODE_CLEAN_TIMEOUT : 1,
        NODE_CHECKOUT_TIMEOUT : 1,
        BUILD_JDK_TIMEOUT : 12,
        BUILD_ARCHIVE_TIMEOUT : 3,
        CONTROLLER_CLEAN_TIMEOUT : 1,
        DOCKER_CHECKOUT_TIMEOUT : 1,
        DOCKER_PULL_TIMEOUT : 2,
        ARCHIVE_ARTIFACTS_TIMEOUT : 6
    ]

    /*
    Constructor
    */
    Build(
        IndividualBuildConfig buildConfig,
        Map USER_REMOTE_CONFIGS,
        Map DEFAULTS_JSON,
        Map ADOPT_DEFAULTS_JSON,
        def context,
        def env,
        def currentBuild
    ) {
        this.buildConfig = buildConfig
        this.USER_REMOTE_CONFIGS = USER_REMOTE_CONFIGS
        this.DEFAULTS_JSON = DEFAULTS_JSON
        this.ADOPT_DEFAULTS_JSON = ADOPT_DEFAULTS_JSON
        this.context = context
        this.currentBuild = currentBuild
        this.env = env
    }

    // Workaround to handle different versions of Badge plugin
    def appendSummaryText(summary, text) {
        try {
                def currentText = summary.getText() ?: ""
                summary.setText(currentText + text)
        } catch (Exception e) {
                echo "setText failed, trying deprecated appendText: ${e.message}"
                summary.appendText(text, false)
        }
    }

    /*
    Returns the java version number for this job (e.g. 8, 11, 17, ...)
    */
    Integer getJavaVersionNumber() {
        def javaToBuild = buildConfig.JAVA_TO_BUILD
        // version should be something like "jdk8u" or "jdk" for HEAD
        Matcher matcher = javaToBuild =~ /.*?(?<version>\d+).*?/
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group('version'))
        } else if ('jdk'.equalsIgnoreCase(javaToBuild.trim())) {
            int headVersion
            try {
                context.timeout(time: buildTimeouts.API_REQUEST_TIMEOUT, unit: 'HOURS') {
                    // Query the Adopt api to get the "tip_version"
                    String helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
                    def JobHelper = context.library(identifier: "openjdk-jenkins-helper@${helperRef}").JobHelper
                    context.println 'Querying Adopt Api for the JDK-Head number (tip_version)...'

                    def response = JobHelper.getAvailableReleases(context)
                    headVersion = (int) response[('tip_version')]
                    context.println "Found Java Version Number: ${headVersion}"
                }
            } catch (FlowInterruptedException e) {
                throw new Exception("[ERROR] Adopt API Request timeout (${buildTimeouts.API_REQUEST_TIMEOUT} HOURS) has been reached. Exiting...")
            }
            return headVersion
        } else {
            throw new Exception("Failed to read java version '${javaToBuild}'")
        }
    }

    /*
    Calculates which test job we should execute for each requested test type.
    The test jobs all follow the same name naming pattern that is defined in the aqa-tests repository.
    E.g. Test_openjdk11_hs_sanity.system_ppc64_aix
    */
    def getSmokeTestJobParams() {
        def jobParams = getCommonTestJobParams()
        jobParams.put('LEVELS', 'extended')
        jobParams.put('GROUPS', 'functional')
        jobParams.put('TEST_JOB_NAME', "${env.JOB_NAME}_SmokeTests")
        jobParams.put('BUILD_LIST', 'functional/buildAndPackage')
        def vendorTestRepos = ((String)ADOPT_DEFAULTS_JSON['repository']['build_url']) - ('.git')
        def vendorTestDirs = ADOPT_DEFAULTS_JSON['repository']['test_dirs']
        def vendorTestBranches = ADOPT_DEFAULTS_JSON['repository']['build_branch']
        jobParams.put('VENDOR_TEST_REPOS', vendorTestRepos)
        jobParams.put('VENDOR_TEST_DIRS', vendorTestDirs)
        jobParams.put('VENDOR_TEST_BRANCHES', vendorTestBranches)
        return jobParams
    }

    def getCommonTestJobParams() {
        def jobParams = [:]
        String jdk_Version = getJavaVersionNumber() as String
        jobParams.put('JDK_VERSIONS', jdk_Version)

        if (buildConfig.VARIANT == 'temurin') {
            jobParams.put('JDK_IMPL', 'hotspot')
        } else {
            jobParams.put('JDK_IMPL', buildConfig.VARIANT)
        }

        def arch = buildConfig.ARCHITECTURE
        if (arch == 'x64') {
            arch = 'x86-64'
        }

        // Default to a 25 hours for all test jobs
        jobParams.put('TIME_LIMIT', '25')

        def arch_os = "${arch}_${buildConfig.TARGET_OS}"
        jobParams.put('ARCH_OS_LIST', arch_os)
        jobParams.put('LIGHT_WEIGHT_CHECKOUT', true)
        return jobParams
    }
    
    /*
      If the given result is not SUCCESS then set the current stage result and build result accordingly
    */
    def setStageResult(String stage, String result) {
        if (result != "SUCCESS") {
            // Use catchError to issue error message and set build & stage result
            context.catchError(buildResult: result, stageResult: result) {
                context.error("${stage} not successful, setting stage result to: "+result)
            }
        } else {
            context.println "${stage} result was SUCCESS"
        }
    }

    /*
    Run smoke tests, which should block the running of downstream test jobs if there are failures.
    If a test job that doesn't exist, it will be created dynamically.
    */
    def runSmokeTests() {
        def additionalTestLabel = buildConfig.ADDITIONAL_TEST_LABEL
        def useAdoptShellScripts = Boolean.valueOf(buildConfig.USE_ADOPT_SHELL_SCRIPTS)
        def vendorTestBranches = useAdoptShellScripts ? ADOPT_DEFAULTS_JSON['repository']['build_branch'] : DEFAULTS_JSON['repository']['build_branch']
        def vendorTestRepos = useAdoptShellScripts ? ADOPT_DEFAULTS_JSON['repository']['build_url'] :  DEFAULTS_JSON['repository']['build_url']
        vendorTestRepos = vendorTestRepos - ('.git')

        // Use BUILD_REF override if specified
        vendorTestBranches = buildConfig.BUILD_REF ?: vendorTestBranches

        try {
            context.println 'Running smoke test'
            context.stage('smoke test') {
                def jobParams = getSmokeTestJobParams()
                def jobName = jobParams.TEST_JOB_NAME
                String helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
                def JobHelper = context.library(identifier: "openjdk-jenkins-helper@${helperRef}").JobHelper
                if (!JobHelper.jobIsRunnable(jobName as String)) {
                    context.node('worker') {
                        context.sh('curl -Os https://raw.githubusercontent.com/adoptium/aqa-tests/master/buildenv/jenkins/testJobTemplate')
                        def templatePath = 'testJobTemplate'
                        context.println "Smoke test job doesn't exist, create test job: ${jobName}"
                        context.jobDsl targets: templatePath, ignoreExisting: false, additionalParameters: jobParams
                    }
                }

                def testJobParamsMap = [
                    UPSTREAM_JOB_NUMBER: "${env.BUILD_NUMBER}",
                    UPSTREAM_JOB_NAME: "${env.JOB_NAME}",
                    SDK_RESOURCE: 'upstream',
                    JDK_VERSION: "${jobParams.JDK_VERSIONS}",
                    LABEL_ADDITION: "${additionalTestLabel}",
                    KEEP_REPORTDIR: "${buildConfig.KEEP_TEST_REPORTDIR}",
                    ACTIVE_NODE_TIMEOUT: "${buildConfig.ACTIVE_NODE_TIMEOUT}",
                    DYNAMIC_COMPILE: "true",
                    VENDOR_TEST_REPOS: "${vendorTestRepos}",
                    VENDOR_TEST_BRANCHES: "${vendorTestBranches}",
                    TIME_LIMIT: '1'
                ]

                def additionalTestParams = buildConfig.ADDITIONAL_TEST_PARAMS
                if (Map.isInstance(additionalTestParams)) {
                    additionalTestParams.each { additionalParam, additionalParamValue ->
                        testJobParamsMap[(additionalParam)] = additionalParamValue.toString()
                    }
                }
                
                def testJobParams = []
                testJobParamsMap.each { paramKey, paramValue ->
                    if (paramValue == 'true' || paramValue == 'false') {
                        testJobParams << context.booleanParam(name: paramKey, value: paramValue.toBoolean())
                    } else {
                        testJobParams << context.string(name: paramKey, value: paramValue)
                    }
                }

                def testJob = context.build job: jobName,
                    propagate: false,
                    parameters: testJobParams
                    
                currentBuild.result = testJob.getResult()
                setStageResult("smoke test", testJob.getResult())
                return testJob.getResult()
            }
        } catch (Exception e) {
            context.println "Failed to execute test: ${e.message}"
            throw new Exception('[ERROR] Smoke Tests failed indicating a problem with the build artifact. No further tests will run until Smoke test failures are fixed. ')
        }
    }

    /*
    Run the downstream test jobs based off the configuration passed down from the top level pipeline jobs.
    If a test job doesn't exist, it will be created dynamically.
    */
    def runAQATests(jdkFileName) {
        def aqaBranch = 'master'
        def build_type = 'nightly'
        def testImageName = jdkFileName.replace('-jdk_', '-testimage_')
       // def staticLibName = jdkFileName.replace('-jdk_', '-static-libs_')
        def sdkUrl = "${env.BUILD_URL}/artifact/workspace/target/${jdkFileName} ${env.BUILD_URL}/artifact/workspace/target/${testImageName}"
        def aqaTestPipelineJobName = "AQA_Test_Pipeline"
        def releaseAppendix = ''
        if (buildConfig.SCM_REF && buildConfig.AQA_REF) {
            aqaBranch = buildConfig.AQA_REF  
            releaseAppendix = "_RELEASE" 
        }
        if (Boolean.valueOf(buildConfig.RELEASE)) {
            build_type = 'release'
            aqaTestPipelineJobName = "AQA_Test_Pipeline${releaseAppendix}"
        } else if (Boolean.valueOf(buildConfig.WEEKLY)) {
            build_type = 'weekly'
        }

        try {
            
            def jobParams = getCommonTestJobParams()
            def displayName = "jdk${jobParams.JDK_VERSIONS} : ${buildConfig.SCM_REF}${releaseAppendix} : ${jobParams.ARCH_OS_LIST}"
            def useAdoptShellScripts = Boolean.valueOf(buildConfig.USE_ADOPT_SHELL_SCRIPTS)
            def vendorTestBranches = useAdoptShellScripts ? ADOPT_DEFAULTS_JSON['repository']['build_branch'] : DEFAULTS_JSON['repository']['build_branch']
            def vendorTestRepos = useAdoptShellScripts ? ADOPT_DEFAULTS_JSON['repository']['build_url'] :  DEFAULTS_JSON['repository']['build_url']
            vendorTestRepos = vendorTestRepos - ('.git')
            def vendorTestDirs = '/test/system'
            // Use BUILD_REF override if specified
            vendorTestBranches = buildConfig.BUILD_REF ?: vendorTestBranches
            context.echo " Temurin AQA_Test_Pipeline${releaseAppendix} job : ${displayName}"                                    
            def aqaJob = context.build job: "${aqaTestPipelineJobName}",
                propagate: false,
                parameters: [
                    context.string(name: 'SDK_RESOURCE', value: 'customized'),
                    context.string(name: 'CUSTOMIZED_SDK_URL', value: "${sdkUrl}"),
                    context.string(name: 'ADOPTOPENJDK_BRANCH', value: "${aqaBranch}"),
                    context.string(name: 'VENDOR_TEST_REPOS', value: "${vendorTestRepos}"),
                    context.string(name: 'VENDOR_TEST_BRANCHES', value: "${vendorTestBranches}"),
                    context.string(name: 'VENDOR_TEST_DIRS', value: "${vendorTestDirs}"),
                    context.string(name: 'JDK_VERSIONS', value: "${jobParams.JDK_VERSIONS}"),
                    context.string(name: 'BUILD_TYPE', value: "${build_type}"),
                    context.string(name: 'VARIANT', value: "${buildConfig.VARIANT}"),
                    context.string(name: 'PLATFORMS', value: "${jobParams.ARCH_OS_LIST}"),
                    context.string(name: 'PIPELINE_DISPLAY_NAME', value: "${displayName}")
                ],
                wait: false,
                waitForStart: true
            if (aqaJob?.absoluteUrl && aqaJob?.number) {
                context.currentBuild.description = (context.currentBuild.description ?: '') + "<br><a href='${aqaJob.absoluteUrl}'>${aqaTestPipelineJobName} #${aqaJob.number}</a>"
            } else {
                def aqaJobUrl = "${context.JENKINS_URL}job/${aqaTestPipelineJobName}/"
                context.currentBuild.description = (context.currentBuild.description ?: '') + "<br><a href='${aqaJobUrl}'>${aqaTestPipelineJobName} (no build number available)</a>"
            }

        } catch (Exception e) {
            context.println "Failed to execute test: ${e.message}"
            currentBuild.result = 'FAILURE'
        }
    }

    // Temurin remote jck trigger
    def remoteTriggerJckTests(String jdkFileName) {
        def jobParams = getCommonTestJobParams()
        def sdkUrl = "${env.BUILD_URL}/artifact/workspace/target/${jdkFileName}"
        def build_type = 'weekly'
        if (Boolean.valueOf(buildConfig.RELEASE)) {
            build_type = 'release'
        }
        try {

            def displayName = "jdk${jobParams.JDK_VERSIONS} : ${buildConfig.SCM_REF} : ${build_type} : ${jobParams.ARCH_OS_LIST}"
            context.echo " Temurin AQA_Test_Pipeline_JCK job : ${displayName}"                                    
            def jckJob = context.build job: 'AQA_Test_Pipeline_JCK',
                propagate: false,
                parameters: [
                    context.string(name: 'SDK_RESOURCE', value: 'customized'),
                    context.string(name: 'CUSTOMIZED_SDK_URL', value: "${sdkUrl}"),
                    context.string(name: 'JDK_VERSIONS', value: "${jobParams.JDK_VERSIONS}"),
                    context.string(name: 'PLATFORMS', value: "${jobParams.ARCH_OS_LIST}"),
                    context.string(name: 'PIPELINE_DISPLAY_NAME', value: "${displayName}"),
                    context.string(name: 'BUILD_TYPE', value: "${build_type}")
                ],
                wait: false,
                waitForStart: true
            if (jckJob?.absoluteUrl && jckJob?.number) {
                context.currentBuild.description = (context.currentBuild.description ?: '') + "<br><a href='${jckJob.absoluteUrl}'>AQA_Test_Pipeline_JCK #${jckJob.number}</a>"
            } else {
                def jckJobUrl = "${context.JENKINS_URL}job/AQA_Test_Pipeline_JCK/"
                context.currentBuild.description = (context.currentBuild.description ?: '') + "<br><a href='${jckJobUrl}'>AQA_Test_Pipeline_JCK (no build number available)</a>"
            }

        } catch (Exception e) {
            context.println "Failed to remote trigger jck tests: ${e.message}"
        }
    }

    def compareReproducibleBuild(String nonDockerNodeName) {
        // Currently only enable for jdk17, linux_x64, temurin, nightly, which shouldn't affect current build
        // Move out of normal jdk** folder as it won't be regenerated automatically right now
        def buildJobName = "${env.JOB_NAME}"
        buildJobName = buildJobName.substring(buildJobName.lastIndexOf('/')+1)
        def comparedJobName = "${buildJobName}_reproduce_compare"
        if (!Boolean.valueOf(buildConfig.RELEASE)) {
            // For now set the build as independent, no need to wait for result as the build takes time
            String helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
            def JobHelper = context.library(identifier: "openjdk-jenkins-helper@${helperRef}").JobHelper
            if (!JobHelper.jobIsRunnable(comparedJobName as String)) {
                context.node('worker') {
                    context.println "Reproduce_compare job doesn't exist, create reproduce_compare job: ${comparedJobName}"
                    context.jobDsl scriptText: """
                        pipelineJob("${comparedJobName}") {
                            description(\'<h1>THIS IS AN AUTOMATICALLY GENERATED JOB. PLEASE DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1>\')

                            definition {
                                parameters {
                                    stringParam("COMPARED_JOB_NUMBER", "", "Compared nightly build job number.")
                                    stringParam("COMPARED_JOB_NAME", "", "Compared nightly build job name")
                                    stringParam("COMPARED_AGENT", "", "Compared nightly build job agent.")
                                    stringParam("COMPARED_JOB_PARAMS", "", "Compared nightly build job parameters")
                                }
                                cpsScm {
                                    scm {
                                        git {
                                            remote {
                                                url("https://github.com/adoptium/ci-jenkins-pipelines.git")
                                            }
                                            branch("*/master")
                                        }
                                        scriptPath("tools/reproduce_comparison/Jenkinsfile")
                                        lightweight(true)
                                    }
                                }
                                logRotator {
                                    numToKeep(30)
                                    artifactNumToKeep(10)
                                    daysToKeep(60)
                                    artifactDaysToKeep(10)
                                }
                            }
                        }
                    """ , ignoreExisting: false
                }
            }
            context.stage('Reproduce Compare') {
                def buildParams = context.params.toString()
                // passing buildParams multiline parameter to downstream job, double check the available method
                context.build job: comparedJobName,
                                propagate: false,
                                parameters: [
                                    context.string(name: 'COMPARED_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                                    context.string(name: 'COMPARED_JOB_NAME', value: "${env.JOB_NAME}"),
                                    context.string(name: 'COMPARED_AGENT', value: nonDockerNodeName),
                                    context.string(name: 'COMPARED_JOB_PARAMS', value: buildParams)
                                ],
                                wait: false
            }
        }
    }

    /*
    We use this function at the end of a build to parse a java version string and create a VersionInfo object for deployment in the metadata objects.
    E.g. 11.0.9+10-202010192351 would be one example of a matched string.
    The regex would match both OpenJDK Runtime Environment and Java(TM) SE Runtime Environment.
    */
    VersionInfo parseVersionOutput(String consoleOut) {
        context.println(consoleOut)
        Matcher matcher = (consoleOut =~ /(?ms)^.*Runtime Environment[^\n]*\(build (?<version>[^)]*)\).*$/)
        if (matcher.matches()) {
            context.println('matched')
            String versionOutput = matcher.group('version')

            context.println(versionOutput)

            return new VersionInfo(context).parse(versionOutput, buildConfig.ADOPT_BUILD_NUMBER)
        }
        return null
    }

    /*
    Run the Sign downstream job. We run this job on windows and jdk8 hotspot & jdk13 mac builds.
    The job code signs and notarizes the binaries so they can run on these operating systems without encountering issues.
    */
    def sign(VersionInfo versionInfo) {
        // Sign and archive jobs if needed
        if (
            buildConfig.TARGET_OS == 'windows' || (buildConfig.TARGET_OS == 'mac')
        ) {
            context.stage('sign zip/tgz') {
                def filter = ''

                def nodeFilter = 'eclipse-codesign'

                if (buildConfig.TARGET_OS == 'windows') {
                    filter = '**/OpenJDK*_windows_*.zip'
                } else if (buildConfig.TARGET_OS == 'mac') {
                    filter = '**/OpenJDK*_mac_*.tar.gz'
                }

                def params = [
                        context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                        context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                        context.string(name: 'OPERATING_SYSTEM', value: "${buildConfig.TARGET_OS}"),
                        context.string(name: 'VERSION', value: "${versionInfo.major}"),
                        context.string(name: 'SIGN_TOOL', value: 'eclipse'),
                        context.string(name: 'FILTER', value: "${filter}"),
                        ['$class': 'LabelParameterValue', name: 'NODE_LABEL', label: "${nodeFilter}"],
                ]

                // Execute sign job
                def signJob = context.build job: 'build-scripts/release/sign_build',
                    propagate: true,
                    parameters: params

                context.node('worker') {
                    //Copy signed artifact back and archive again
                    context.sh 'rm workspace/target/* || true'

                    context.copyArtifacts(
                            projectName: 'build-scripts/release/sign_build',
                            selector: context.specific("${signJob.getNumber()}"),
                            filter: 'workspace/target/*',
                            fingerprintArtifacts: true,
                            target: 'workspace/target/',
                            flatten: true)

                    context.sh 'for file in $(ls workspace/target/*.tar.gz workspace/target/*.zip); do sha256sum "$file" > $file.sha256.txt ; done'
                    writeMetadata(versionInfo, false)
                    context.archiveArtifacts artifacts: 'workspace/target/*'
                }
            }
        }
    }

    /*
    Run the Mac installer downstream job.
    */
    private void buildMacInstaller(VersionInfo versionData) {
        def filter = '**/OpenJDK*_mac_*.tar.gz'

        // Execute installer job
        def installerJob = context.build job: 'build-scripts/release/create_installer_mac',
                propagate: true,
                parameters: [
                        context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                        context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                        context.string(name: 'FILTER', value: "${filter}"),
                        context.string(name: 'FULL_VERSION', value: "${versionData.version}"),
                        context.string(name: 'MAJOR_VERSION', value: "${versionData.major}")
                ]

        context.copyArtifacts(
                projectName: 'build-scripts/release/create_installer_mac',
                selector: context.specific("${installerJob.getNumber()}"),
                filter: 'workspace/target/*',
                fingerprintArtifacts: true,
                target: 'workspace/target/',
                flatten: true)
    }

    /*
    Run the Windows installer downstream jobs.
    We run two jobs if we have a JRE (see https://github.com/adoptium/temurin-build/issues/1751).
    */
    private void buildWindowsInstaller(VersionInfo versionData, String filter, String category) {
        def buildNumber = versionData.build

        if (versionData.major == 8) {
            buildNumber = String.format('%02d', versionData.build)
        }

        def INSTALLER_ARCH = "${buildConfig.ARCHITECTURE}"
        // Wix toolset requires aarch64 builds to be called arm64
        if (buildConfig.ARCHITECTURE == 'aarch64') {
            INSTALLER_ARCH = 'arm64'
        }

        // Get version patch number if one is present
        def patch_version = versionData.patch ?: 0

        def INSTALLER_JVM = "${buildConfig.VARIANT}"
        // if variant is temurin set param as hotpot
        if (buildConfig.VARIANT == 'temurin') {
            INSTALLER_JVM = 'hotspot'
        }

        // Execute installer job
        def installerJob = context.build job: 'build-scripts/release/create_installer_windows',
                propagate: true,
                parameters: [
                        context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                        context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                        context.string(name: 'FILTER', value: "${filter}"),
                        context.string(name: 'PRODUCT_MAJOR_VERSION', value: "${versionData.major}"),
                        context.string(name: 'PRODUCT_MINOR_VERSION', value: "${versionData.minor}"),
                        context.string(name: 'PRODUCT_MAINTENANCE_VERSION', value: "${versionData.security}"),
                        context.string(name: 'PRODUCT_PATCH_VERSION', value: "${patch_version}"),
                        context.string(name: 'PRODUCT_BUILD_NUMBER', value: "${buildNumber}"),
                        context.string(name: 'MSI_PRODUCT_VERSION', value: "${versionData.msi_product_version}"),
                        context.string(name: 'PRODUCT_CATEGORY', value: "${category}"),
                        context.string(name: 'JVM', value: "${INSTALLER_JVM}"),
                        context.string(name: 'ARCH', value: "${INSTALLER_ARCH}"),
                ]
        context.copyArtifacts(
                projectName: 'build-scripts/release/create_installer_windows',
                selector: context.specific("${installerJob.getNumber()}"),
                filter: 'wix/ReleaseDir/*',
                fingerprintArtifacts: true,
                target: 'workspace/target/',
                flatten: true)
    }

    /*
    Build installer master function. This builds the downstream installer jobs on completion of the sign and test jobs.
    The installers create our rpm, msi and pkg files that allow for an easier installation of the jdk binaries over a compressed archive.
    For Mac, we also clean up pkgs on master node from previous runs, if needed (Ref openjdk-build#2350).
    */
    def buildInstaller(VersionInfo versionData) {
        if (versionData == null || versionData.major == null) {
            context.println 'Failed to parse version number, possibly a nightly? Skipping installer steps'
            return
        }

        context.node('worker') {
            context.stage('installer') {
                switch (buildConfig.TARGET_OS) {
                    case 'mac':
                        context.sh 'rm -rf workspace/target/* || true'
                        buildMacInstaller(versionData)
                        break
                    case 'windows':
                        context.sh 'rm -rf workspace/target/* || true'
                        buildWindowsInstaller(versionData, '**/OpenJDK*jdk_*_windows*.zip', 'jdk')
                        // Copy jre artifact from current pipeline job
                        context.copyArtifacts(
                            projectName: "${env.JOB_NAME}",
                            selector: context.specific("${env.BUILD_NUMBER}"),
                            filter: '**/OpenJDK*jre_*_windows*.zip',
                            fingerprintArtifacts: true,
                            target: 'workspace/target/',
                            flatten: true)
                        // Check if JRE exists, if so, build another installer for it
                        if (listArchives().any { it =~ /-jre/ } ) { buildWindowsInstaller(versionData, '**/OpenJDK*jre_*_windows*.zip', 'jre') }
                        break
                    default:
                        break
                }

                // Archive the Mac and Windows pkg/msi
                if (buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') {
                    try {
                        context.sh 'for file in $(ls workspace/target/*.tar.gz workspace/target/*.pkg workspace/target/*.msi); do sha256sum "$file" > $file.sha256.txt ; done'
                        writeMetadata(versionData, false)
                        context.archiveArtifacts artifacts: 'workspace/target/*'
                    } catch (e) {
                        context.println("Failed to build ${buildConfig.TARGET_OS} installer ${e}")
                        currentBuild.result = 'FAILURE'
                        setStageResult("installer", 'FAILURE')
                    }
                }
            }
        }
    }

    def signInstaller(VersionInfo versionData) {
        if (versionData == null || versionData.major == null) {
            context.println 'Failed to parse version number, possibly a nightly? Skipping installer steps'
            return
        }

        context.node('worker') {
            context.stage('sign installer') {
                // Ensure master context workspace is clean of any previous archives
                context.sh 'rm -rf workspace/target/* || true'

                if (buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') {
                    try {
                        signInstallerJob(versionData)
                        context.sh 'for file in $(ls workspace/target/*.tar.gz workspace/target/*.pkg workspace/target/*.msi); do sha256sum "$file" > $file.sha256.txt ; done'
                        writeMetadata(versionData, false)
                        context.archiveArtifacts artifacts: 'workspace/target/*'
                    } catch (e) {
                        context.println("Failed to build ${buildConfig.TARGET_OS} installer ${e}")
                        currentBuild.result = 'FAILURE'
                        setStageResult("sign installer", 'FAILURE')
                    }
                }
            }
        }
    }

    private void signInstallerJob(VersionInfo versionData) {
        def filter = ''

        switch (buildConfig.TARGET_OS) {
            case 'mac': filter = '**/OpenJDK*_mac_*.pkg'; break
            case 'windows': filter = '**/OpenJDK*_windows_*.msi'; break
            default: break
        }

        // Execute sign installer job
        def installerJob = context.build job: 'build-scripts/release/sign_installer',
                propagate: true,
                parameters: [
                        context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                        context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                        context.string(name: 'FILTER', value: "${filter}"),
                        context.string(name: 'FULL_VERSION', value: "${versionData.version}"),
                        context.string(name: 'OPERATING_SYSTEM', value: "${buildConfig.TARGET_OS}"),
                        context.string(name: 'MAJOR_VERSION', value: "${versionData.major}")
                ]

        context.copyArtifacts(
                projectName: 'build-scripts/release/sign_installer',
                selector: context.specific("${installerJob.getNumber()}"),
                filter: 'workspace/target/*',
                fingerprintArtifacts: true,
                target: 'workspace/target/',
                flatten: true)
    }

    // For Windows and Mac verify that all necessary executables are Signed and Notarized(mac)
    private void verifySigning() {
        if (buildConfig.TARGET_OS == "windows" || buildConfig.TARGET_OS == "mac") {
          context.stage('sign verification') {
            try {
                context.println "RUNNING sign_verification for ${buildConfig.TARGET_OS}/${buildConfig.ARCHITECTURE} ..."

                // Determine suitable node to run on
                def verifyNode
                if (buildConfig.TARGET_OS == "windows") {
                    verifyNode = "((ci.role.test&&sw.os.windows)||(ci.agent.dynamic&&sw.os.windows.2022))"
                } else {
                    verifyNode = "ci.role.test&&(sw.os.osx||sw.os.mac)"
                }
                if (buildConfig.ARCHITECTURE == "aarch64") {
                    verifyNode = verifyNode + "&&hw.arch.aarch64"
                } else {
                    verifyNode = verifyNode + "&&hw.arch.x86"
                }

                // Execute sign verification job
                context.build job: 'build-scripts/release/sign_verification',
                    propagate: true,
                    parameters: [
                            context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                            context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                            context.string(name: 'TARGET_OS', value: "${buildConfig.TARGET_OS}"),
                            context.string(name: 'TARGET_ARCH', value: "${buildConfig.ARCHITECTURE}"),
                            context.string(name: 'NODE_LABEL', value: "${verifyNode}")
                    ]
            } catch (e) {
                context.println("Failed to sign_verification for ${buildConfig.TARGET_OS}/${buildConfig.ARCHITECTURE} ${e}")
                currentBuild.result = 'FAILURE'
                setStageResult("sign verification", 'FAILURE')
            }
          }
        }
    }

    private void gpgSign() {
        context.stage('GPG sign') {
            context.println "RUNNING sign_temurin_gpg for ${buildConfig.TARGET_OS}/${buildConfig.ARCHITECTURE} ..."

            def params = [
                  context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                  context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                  context.string(name: 'UPSTREAM_DIR', value: 'workspace/target')
           ]

            def signSHAsJob = context.build job: 'build-scripts/release/sign_temurin_gpg',
               propagate: true,
               parameters: params

            context.node('worker') {
                // Remove any previous workspace artifacts
                context.sh 'rm -rf workspace/target/* || true'
                context.copyArtifacts(
                    projectName: 'build-scripts/release/sign_temurin_gpg',
                    selector: context.specific("${signSHAsJob.getNumber()}"),
                    filter: '**/*.sig',
                    fingerprintArtifacts: true,
                    target: 'workspace/target/',
                    flatten: true)

                // Archive GPG signatures in Jenkins
                try {
                    context.timeout(time: buildTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT, unit: 'HOURS') {
                        context.archiveArtifacts artifacts: 'workspace/target/*.sig'
                    }
               } catch (FlowInterruptedException e) {
                    throw new Exception("[ERROR] Archive artifact timeout (${buildTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT} HOURS) for ${downstreamJobName} has been reached. Exiting...")
                }
            }
        }
    }

    // Kick off the sign_temurin_jsf job to sign the SBOM
    private void jsfSignSBOM() {
        context.stage('SBOM JSF Sign') {

            context.println "Running build_sign_sbom_libraries to build the SBOM libraries"
            def buildSBOMLibrariesJob = context.build job: 'build_sign_sbom_libraries',
                propagate: true

            def paramsJsf = [
                  context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                  context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                  context.string(name: 'UPSTREAM_DIR', value: 'workspace/target'),
                  context.string(name: 'SBOM_LIBRARY_JOB_NUMBER', value: "${buildSBOMLibrariesJob.getNumber()}")
           ]

            context.println "RUNNING sign_temurin_jsf for ${buildConfig.TARGET_OS}/${buildConfig.ARCHITECTURE} ..."
            def signSBOMJob = context.build job: 'build-scripts/release/sign_temurin_jsf',
               propagate: true,
               parameters: paramsJsf

            context.node('worker') {
                // Remove any previous workspace artifacts
                context.sh 'rm -rf workspace/target/* || true'
                context.copyArtifacts(
                    projectName: 'build-scripts/release/sign_temurin_jsf',
                    selector: context.specific("${signSBOMJob.getNumber()}"),
                    filter: '**/*sbom*.json',
                    fingerprintArtifacts: true,
                    target: 'workspace/target/',
                    flatten: true)

                // Archive SBOM signatures in Jenkins
                try {
                    context.timeout(time: buildTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT, unit: 'HOURS') {
                        context.archiveArtifacts artifacts: 'workspace/target/*sbom*.json'
                    }
               } catch (FlowInterruptedException e) {
                    throw new Exception("[ERROR] Archive artifact timeout (${buildTimeouts.ARCHIVE_ARTIFACTS_TIMEOUT} HOURS) for ${downstreamJobName} has been reached. Exiting...")
                }
            }
        }
    }
    /*
    Lists and returns any compressed archived or sbom file contents of the top directory of the build node
    */
    List<String> listArchives() {

        def files
        if ( context.isUnix() ) {
           files = context.sh(
                script: '''find workspace/target/ | egrep -e '(\\.tar\\.gz|\\.zip|\\.msi|\\.pkg|\\.deb|\\.rpm|-sbom_.*\\.json)$' ''',
                returnStdout: true,
                returnStatus: false
           ).trim().split('\n').toList()
        } else {
           // The grep here removes anything that still contains "*" because nothing matched
           files = context.bat(
                script: 'dir/b/s workspace\\target\\*.zip workspace\\target\\*.msi workspace\\target\\*.-sbom_* workspace\\target\\*.json',
                returnStdout: true,
                returnStatus: false
           ).trim().replaceAll('\\\\','/').replaceAll('\\r','').split('\n').toList().grep( ~/^[^\*]*$/ ) // grep needed extra script approval
        }
        context.println "listArchives: ${files}"
        return files
    }

    /*
    On any writeMetadata other than the first, we simply return a MetaData object from the previous writeout adjusted to the situation.
    On the first writeout, we pull in the .txt files created by the build that list the attributes of what we used to build the jdk (e.g. configure args, commit hash, etc)
    */
    MetaData formMetadata(VersionInfo version, Boolean initialWrite) {
        // We have to setup some attributes for the first run since formMetadata is sometimes initiated from downstream job on master node with no access to the required files
        if (initialWrite) {
            // Get scmRef
            context.println 'INFO: FIRST METADATA WRITE OUT! Checking if we have a scm reference in the build config...'

            String scmRefPath = 'workspace/target/metadata/scmref.txt'
            scmRef = buildConfig.SCM_REF

            if (scmRef != '') {
                // Use the buildConfig scmref if it is set
                context.println "SUCCESS: SCM_REF has been set (${buildConfig.SCM_REF})! Using it to build the initial metadata over ${scmRefPath}..."
            } else {
                // If we don't have a scmref set in config, check if we have a scmref from the build
                context.println "INFO: SCM_REF is NOT set. Attempting to read ${scmRefPath}..."
                try {
                    scmRef = context.readFile(scmRefPath).trim()
                    context.println "SUCCESS: scmref.txt found: ${scmRef}"
                } catch (NoSuchFileException e) {
                    // In rare cases, we will fail to create the scmref.txt file
                    context.println "WARNING: $scmRefPath was not found. Using build config SCM_REF instead (even if it's empty)..."
                }
            }

            // Get Full Version Output
            String versionPath = 'workspace/target/metadata/version.txt'
            if (buildConfig.BUILD_ARGS.contains('--cross-compile')) {
                versionPath = crossCompileVersionPath
            }
            context.println "INFO: Attempting to read ${versionPath}..."

            try {
                fullVersionOutput = context.readFile(versionPath)
                context.println "SUCCESS: ${versionPath} found"
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${versionPath} was not found. Exiting...")
            }

            // Get Configure Args
            String configurePath = 'workspace/target/metadata/configure.txt'
            context.println "INFO: Attempting to read ${configurePath}..."

            try {
                configureArguments = context.readFile(configurePath)
                context.println 'SUCCESS: configure.txt found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${configurePath} was not found. Exiting...")
            }

            // Get make command args
            String makeCommandArgPath = 'workspace/target/metadata/makeCommandArg.txt'
            context.println "INFO: Attempting to read ${makeCommandArgPath}..."

            try {
                makeCommandArgs = context.readFile(makeCommandArgPath)
                context.println 'SUCCESS: makeCommandArg.txt found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${makeCommandArgPath} was not found. Exiting...")
            }

            // Get Variant Version for OpenJ9
            if (buildConfig.VARIANT == 'openj9') {
                String j9MajorPath = 'workspace/target/metadata/variant_version/major.txt'
                String j9MinorPath = 'workspace/target/metadata/variant_version/minor.txt'
                String j9SecurityPath = 'workspace/target/metadata/variant_version/security.txt'
                String j9TagsPath = 'workspace/target/metadata/variant_version/tags.txt'

                context.println 'INFO: Build variant openj9 detected...'

                context.println 'INFO: Attempting to read workspace/target/metadata/variant_version/major.txt...'
                try {
                    j9Major = context.readFile(j9MajorPath)
                    context.println 'SUCCESS: major.txt found'
                } catch (NoSuchFileException e) {
                    throw new Exception("ERROR: ${j9MajorPath} was not found. Exiting...")
                }

                context.println 'INFO: Attempting to read workspace/target/metadata/variant_version/minor.txt...'
                try {
                    j9Minor = context.readFile(j9MinorPath)
                    context.println 'SUCCESS: minor.txt found'
                } catch (NoSuchFileException e) {
                    throw new Exception("ERROR: ${j9MinorPath} was not found. Exiting...")
                }

                context.println 'INFO: Attempting to read workspace/target/metadata/variant_version/security.txt...'
                try {
                    j9Security = context.readFile(j9SecurityPath)
                    context.println 'SUCCESS: security.txt found'
                } catch (NoSuchFileException e) {
                    throw new Exception("ERROR: ${j9SecurityPath} was not found. Exiting...")
                }

                context.println 'INFO: Attempting to read workspace/target/metadata/variant_version/tags.txt...'
                try {
                    j9Tags = context.readFile(j9TagsPath)
                    context.println 'SUCCESS: tags.txt found'
                } catch (NoSuchFileException e) {
                    throw new Exception("ERROR: ${j9TagsPath} was not found. Exiting...")
                }

                variantVersion = [major: j9Major, minor: j9Minor, security: j9Security, tags: j9Tags]
            }

            // Get Vendor
            String vendorPath = 'workspace/target/metadata/vendor.txt'
            context.println "INFO: Attempting to read ${vendorPath}..."

            try {
                vendorName = context.readFile(vendorPath)
                context.println 'SUCCESS: vendor.txt found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${vendorPath} was not found. Exiting...")
            }

            // Get Build Source
            String buildSourcePath = 'workspace/target/metadata/buildSource.txt'
            context.println "INFO: Attempting to read ${buildSourcePath}..."

            try {
                buildSource = context.readFile(buildSourcePath)
                context.println 'SUCCESS: buildSource.txt found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${buildSourcePath} was not found. Exiting...")
            }

            // Get OpenJDK Source
            String openjdkSourcePath = 'workspace/target/metadata/openjdkSource.txt'
            context.println "INFO: Attempting to read ${openjdkSourcePath}..."

            try {
                openjdkSource = context.readFile(openjdkSourcePath)
                context.println 'SUCCESS: openjdkSource.txt found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${openjdkSourcePath} was not found. Exiting...")
            }

            // Get built OPENJDK BUILD_CONFIG
            String openjdkBuiltConfigPath = 'workspace/config/built_config.cfg'
            context.println "INFO: Attempting to read ${openjdkBuiltConfigPath}..."
            try {
                openjdk_built_config = context.readFile(openjdkBuiltConfigPath)
                context.println 'SUCCESS: built_config.cfg found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${openjdkBuiltConfigPath} was not found. Exiting...")
            }

            // Get built makejdk-any-platforms.sh args
            String makejdkArgsPath = 'workspace/config/makejdk-any-platform.args'
            context.println "INFO: Attempting to read ${makejdkArgsPath}..."
            try {
                makejdkArgs = context.readFile(makejdkArgsPath)
                context.println 'SUCCESS: makejdk-any-platform.args found'
            } catch (NoSuchFileException e) {
                throw new Exception("ERROR: ${makejdkArgsPath} was not found. Exiting...")
            }

            // Get dependency_versions
            def deps = ['alsa', 'freetype', 'freemarker']
            for (dep in deps) {
                String depVerPath = "workspace/target/metadata/dependency_version_${dep}.txt"
                context.println "INFO: Attempting to read ${depVerPath}..."
                if (context.fileExists(depVerPath)) {
                    def depVer = context.readFile(depVerPath)
                    context.println "SUCCESS: dependency_version_${dep}.txt found: ${depVer}"
                    dependency_version["${dep}"] = depVer
                } else {
                    context.println "${depVerPath} was not found, no metadata set."
                    dependency_version["${dep}"] = ''
                }
            }
        }

        return new MetaData(
            vendorName,
            buildConfig.TARGET_OS,
            scmRef,
            buildSource,
            version,
            buildConfig.JAVA_TO_BUILD,
            buildConfig.VARIANT,
            variantVersion,
            buildConfig.ARCHITECTURE,
            fullVersionOutput,
            makejdkArgs,
            configureArguments,
            makeCommandArgs,
            buildConfig.toJson(),
            openjdk_built_config,
            openjdkSource,
            dockerImageDigest,
            dependency_version['alsa'],
            dependency_version['freetype'],
            dependency_version['freemarker']
        )
    }

    /*
    Calculates and writes out the metadata to a file.
    The metadata defines and summarises a build and the jdk it creates.
    https://api.adoptium.net/ v3 api makes use of it in its endpoints to quickly display information about the jdk binaries that are stored on github.
    */
    def writeMetadata(VersionInfo version, Boolean initialWrite) {
        /*
        example data:
            {
                "vendor": "Eclipse Adoptium",
                "os": "mac",
                "arch": "x64",
                "variant": "openj9",
                "variant_version": {
                    "major": "0",
                    "minor": "22",
                    "security": "0",
                    "tags": "m2"
                },
                "version": {
                    "minor": 0,
                    "security": 0,
                    "pre": null,
                    "adopt_build_number": 0,
                    "major": 15,
                    "version": "15+29-202007070926",
                    "semver": "15.0.0+29.0.202007070926",
                    "build": 29,
                    "opt": "202007070926"
                },
                "scmRef": "<output of git describe OR buildConfig.SCM_REF>",
                "buildRef": "<build-repo-name/build-commit-sha>",
                "version_data": "jdk15",
                "binary_type": "debugimage",
                "sha256": "<shasum>",
                "full_version_output": <output of java --version>,
                "configure_arguments": <output of bash configure>,
                "make_command_args" : <make command args>,
                "BUILD_CONFIGURATION_param": <build job BUILD_CONFIGURATION param json>,
                "openjdk_built_config" : <built BUILD_CONFIG>
            }
        */

        MetaData data = formMetadata(version, initialWrite)
        Boolean metaWrittenOut = false
        listArchives().each({ file ->
            def type = 'jdk'
            if (file.contains('-jre')) {
                type = 'jre'
            } else if (file.contains('-testimage')) {
                type = 'testimage'
            } else if (file.contains('-debugimage')) {
                type = 'debugimage'
            } else if (file.contains('-static-libs')) {
                type = 'staticlibs'
            } else if (file.contains('-sources')) {
                type = 'sources'
            } else if (file.contains('-sbom')) {
                type = 'sbom'
            } else if (file.contains('-jmods')) {
                type = 'jmods'
            }
            context.println "writeMetaData for " + file

            String hash
            if ( context.isUnix() ) {
                context.println "Non-windows non-docker detected - running sh to generate SHA256 sums in writeMetadata"
                hash = context.sh(script: """\
                                              if [ -x "\$(command -v shasum)" ]; then
                                                (shasum -a 256 | cut -f1 -d' ') <$file
                                              else
                                                sha256sum $file | cut -f1 -d' '
                                              fi
                                            """.stripIndent(), returnStdout: true, returnStatus: false).replaceAll('\n', '')
            } else {
                context.println "Windows detected - running bat to generate SHA256 sums in writeMetadata"
                hash = context.bat(script: "@sha256sum ${file}", returnStdout: true, returnStatus: false).split(' ').first()
            }
            context.println "archive sha256 = ${hash}"

            data.binary_type = type
            data.sha256 = hash

            // To save on spam, only print out the metadata the first time
            if (!metaWrittenOut && initialWrite) {
                context.println '===METADATA OUTPUT==='
                context.println JsonOutput.prettyPrint(JsonOutput.toJson(data.asMap()))
                context.println '=/=METADATA OUTPUT=/='
                metaWrittenOut = true
            }

            // Special handling for sbom metadata file (to be backwards compatible for api service)
            // from "*sbom<XXX>.json" to "*sbom<XXX>-metadata.json"
            if (file.contains('sbom')) {
                context.writeFile file: file.replace('.json', '-metadata.json'), text: JsonOutput.prettyPrint(JsonOutput.toJson(data.asMap()))
            } else {
                context.writeFile file: "${file}.json", text: JsonOutput.prettyPrint(JsonOutput.toJson(data.asMap()))
            }
        })
    }

    /*
    Calculates what the binary filename will be based off of the version, arch, os, variant, timestamp and extension.
    It will usually be something like OpenJDK8U-jdk_x64_linux_temurin_2020-10-19-17-06.tar.gz
    */
    def determineFileName() {
        String javaToBuild = buildConfig.JAVA_TO_BUILD
        String architecture = buildConfig.ARCHITECTURE
        String os = buildConfig.TARGET_OS
        String variant = buildConfig.VARIANT
        String additionalFileNameTag = buildConfig.ADDITIONAL_FILE_NAME_TAG
        String overrideFileNameVersion = buildConfig.OVERRIDE_FILE_NAME_VERSION

        def extension = 'tar.gz'

        if (os == 'windows') {
            extension = 'zip'
        }

        javaToBuild = javaToBuild.trim().toUpperCase()

        // Add "U" to javaToBuild filename prefix for non-head versions
        if (!javaToBuild.endsWith('U') && !javaToBuild.equals('JDK')) {
            javaToBuild += 'U'
        }

        def fileName = "Open${javaToBuild}-jdk_${architecture}_${os}_${variant}"
        if (variant == 'temurin') {
            // For compatibility with existing releases
            fileName = "Open${javaToBuild}-jdk_${architecture}_${os}_hotspot"
        }

        if (additionalFileNameTag) {
            fileName = "${fileName}_${additionalFileNameTag}"
        }

        if (overrideFileNameVersion) {
            fileName = "${fileName}_${overrideFileNameVersion}"
        } else if (buildConfig.PUBLISH_NAME) {
            // for java 11 remove jdk- and +. i.e jdk-11.0.3+7 -> 11.0.3_7_openj9-0.14.0
            def nameTag = buildConfig.PUBLISH_NAME
                    .replace('jdk-', '')
                    .replaceAll("\\+", '_')

            // for java 8 remove jdk and - before the build. i.e jdk8u212-b03_openj9-0.14.0 -> 8u212b03_openj9-0.14.0
            nameTag = nameTag
                    .replace('jdk', '')
                    .replace('-b', 'b')

            fileName = "${fileName}_${nameTag}"
        } else {
            def timestamp = new Date().format('yyyy-MM-dd-HH-mm', TimeZone.getTimeZone('UTC'))

            fileName = "${fileName}_${timestamp}"
        }

        fileName = "${fileName}.${extension}"

        context.println "Filename will be: $fileName"
        return fileName
    }

    /*
    Run the cross comile version reader downstream job.
    In short, we archive the build artifacts to expose them to the job and run ./java version, copying the output back to here.
    See cross_compiled_version_out.groovy.
    */
    def readCrossCompiledVersionString() {
        // Archive the artifacts early so we can copy them over to the downstream job
        try {
            context.timeout(time: buildTimeouts.BUILD_ARCHIVE_TIMEOUT, unit: 'HOURS') {
                context.archiveArtifacts artifacts: 'workspace/target/*'
            }
        } catch (FlowInterruptedException e) {
            throw new Exception("[ERROR] Build archive timeout (${buildTimeouts.BUILD_ARCHIVE_TIMEOUT} HOURS) has been reached. Exiting...")
        }

        // Setup params for downstream job & execute
        String shortJobName = env.JOB_NAME.split('/').last()
        String copyFileFilter = "${shortJobName}_${env.BUILD_NUMBER}_version.txt"

        def nodeFilter = "${buildConfig.TARGET_OS}&&${buildConfig.ARCHITECTURE}"

        def filter = ''

        if (buildConfig.TARGET_OS == 'windows') {
            filter = "**\\OpenJDK*-jdk*_windows_*.zip"
        } else {
            filter = "OpenJDK*-jdk*_${buildConfig.TARGET_OS}_*.tar.gz"
        }

        def crossCompileVersionOut = context.build job: 'build-scripts/utils/cross-compiled-version-out',
            propagate: true,
            parameters: [
                context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                context.string(name: 'JDK_FILE_FILTER', value: "${filter}"),
                context.string(name: 'FILENAME', value: "${copyFileFilter}"),
                context.string(name: 'NODE', value: "${nodeFilter}"),
                context.string(name: 'OS', value: "${buildConfig.TARGET_OS}")
            ]

        context.copyArtifacts(
            projectName: 'build-scripts/utils/cross-compiled-version-out',
            selector: context.specific("${crossCompileVersionOut.getNumber()}"),
            filter: "CrossCompiledVersionOuts/${copyFileFilter}",
            target: 'workspace/target/metadata',
            flatten: true
        )

        // We assign to a variable so it can be used in formMetadata() to find the correct version info
        crossCompileVersionPath = "workspace/target/metadata/${copyFileFilter}"
        return context.readFile(crossCompileVersionPath)
    }

    /*
     * In Windows docker containers sh can be unreliable, so use context.bat
     * in preference. https://github.com/adoptium/infrastructure/issues/3714
     */
    def batOrSh(command)
    {
        if ( context.isUnix() ) {
            context.sh(command)
        } else {
            context.bat(command)
        }
    }

    /*
     Display the current git repo information
     */
    def printGitRepoInfo() {
        context.println 'Checked out repo:'
        batOrSh('git status')
        context.println 'Checked out HEAD commit SHA:'
        batOrSh('git rev-parse HEAD')
    }

    /*
     Build the comma separated list of files to be Eclipse signed
     */
    def getEclipseSigningFileList(base_path) {
        def target_os = "${buildConfig.TARGET_OS}"

        def sign_count = 0
        def files_to_sign = ""

        def folders = ["hotspot/variant-server",
                       "support/modules_cmds",
                       "support/modules_libs"
                      ]

        if (target_os == "mac") {
            // jpackage resources are only signed for Mac
            folders.add("jdk/modules/jdk.jpackage/jdk/jpackage/internal/resources")
        }

        folders.each { folder ->
            if (context.fileExists("${base_path}/${folder}")) {
                def files
                if (target_os == "mac") {
                    files = context.sh(script: "find '${base_path}/${folder}/' -perm +111 -type f -o -name '*.dylib' -type f || find '${base_path}/${folder}/' -perm /111 -type f -o -name '*.dylib'  -type f", returnStdout:true).trim().split('\n')
                } else if (target_os == "windows") {
                    files = context.bat(script: "@find '${base_path}/${folder}/' -type f -name '*.exe' -o -name '*.dll'", returnStdout:true).trim().split('\n')
                }

                files.each { file ->
                    if (file.trim() != "") {
                        if (target_os == "mac") {
                            files_to_sign = files_to_sign + file + ","
                            sign_count += 1
                        } else if (target_os == "windows") {
                            String filename = context.bat(script: "@basename '${file}'", returnStdout:true).trim()
                            // Check if file is a Microsoft supplied file that is already signed
                            if ( !filename.startsWith("api-ms-win") && !filename.startsWith("API-MS-Win") && !filename.startsWith("msvcp") && !filename.startsWith("ucrtbase") && !filename.startsWith("vcruntime") ) {
                                files_to_sign = files_to_sign + file + ","
                                sign_count += 1
                            }
                        }
                    }
                }
            }
        }

        files_to_sign = files_to_sign.replaceAll("//", "/")

        context.println "${sign_count} files to be signed: $files_to_sign"

        return files_to_sign
    }

    def buildScriptsEclipseSigner() {
        def build_path
        build_path = 'workspace/build/src/build'
        def base_path
        base_path = build_path
        def repoHandler = new RepoHandler(USER_REMOTE_CONFIGS, ADOPT_DEFAULTS_JSON, buildConfig.CI_REF, buildConfig.BUILD_REF)

        context.stage('internal sign') {
            context.node('eclipse-codesign') {
                // Safety first!
                if (base_path != null && !base_path.isEmpty()) {
                    context.sh "rm -rf ${base_path}/* || true"
                }

                repoHandler.checkoutAdoptBuild(context)
                printGitRepoInfo()

                // Copy pre assembled binary ready for JMODs to be codesigned
                context.unstash 'jmods'
                def target_os = "${buildConfig.TARGET_OS}"
                // TODO: Split this out into a separate script at some point
                context.withEnv(['base_os='+target_os, 'base_path='+base_path]) {
                                            // groovylint-disable
                                            context.sh '''
                                                #!/bin/bash
                                                set -eu
                                                echo "Signing JMOD files under build path ${base_path} for base_os ${base_os}"
                                                TMP_DIR="${base_path}/"
                                                MAC_ENTITLEMENTS="$WORKSPACE/entitlements.plist"
                                                FILES=$(find "${TMP_DIR}" -type f)
                                                for f in $FILES
                                                do
                                                        dir=$(dirname "$f")
                                                        file=$(basename "$f")
                                                        echo "Signing $f using Eclipse Foundation codesign service"
                                                        mv "$f" "${dir}/unsigned_${file}"
                                                        success=false
                                                        if [ "${base_os}" == "mac" ]; then
                                                            if ! curl --fail --silent --show-error -o "$f" -F file="@${dir}/unsigned_${file}" -F entitlements="@$MAC_ENTITLEMENTS" https://cbi.eclipse.org/macos/codesign/sign; then
                                                                echo "curl command failed, sign of $f failed"
                                                            else
                                                                success=true
                                                            fi
                                                        else
                                                            if ! curl --fail --silent --show-error -o "$f" -F file="@${dir}/unsigned_${file}" https://cbi.eclipse.org/authenticode/sign; then
                                                                echo "curl command failed, sign of $f failed"
                                                            else
                                                                success=true
                                                            fi
                                                        fi
                                                        if [ $success == false ]; then
                                                            # Retry up to 20 times
                                                            max_iterations=20
                                                            iteration=1
                                                            echo "Code Not Signed For File $f"
                                                            while [ $iteration -le $max_iterations ] && [ $success = false ]; do
                                                                echo $iteration Of $max_iterations
                                                                sleep 1
                                                                if [ "${base_os}" == "mac" ]; then
                                                                    if curl --fail --silent --show-error -o "$f" -F file="@${dir}/unsigned_${file}" -F entitlements="@$MAC_ENTITLEMENTS" https://cbi.eclipse.org/macos/codesign/sign; then
                                                                        success=true
                                                                    fi
                                                                else
                                                                    if curl --fail --silent --show-error -o "$f" -F file="@${dir}/unsigned_${file}" https://cbi.eclipse.org/authenticode/sign; then
                                                                        success=true
                                                                    fi
                                                                fi

                                                                if [ $success = false ]; then
                                                                    echo "curl command failed, $f Failed Signing On Attempt $iteration"
                                                                    iteration=$((iteration+1))
                                                                    if [ $iteration -gt $max_iterations ]
                                                                    then
                                                                        echo "Errors Encountered During Signing"
                                                                        exit 1
                                                                    fi
                                                                else
                                                                    echo "$f Signed OK On Attempt $iteration"
                                                                fi
                                                            done
                                                        fi
                                                        chmod --reference="${dir}/unsigned_${file}" "$f"
                                                        rm -rf "${dir}/unsigned_${file}"
                                                done
                                            '''
                                            // groovylint-enable
                }
                context.sh(script: "ls -l ${base_path}/**/*")
                context.stash name: 'signed_jmods', includes: "${base_path}/**/*"
            } // context.node ("eclipse-codesign") - joe thinks it matches with something else though ...
        } // context.stage
}

def postBuildWSclean(
    cleanWorkspaceAfter,
    cleanWorkspaceBuildOutputAfter
) {
                // post-build workspace clean:
                if (cleanWorkspaceAfter || cleanWorkspaceBuildOutputAfter) {
                    try {
                        context.timeout(time: buildTimeouts.NODE_CLEAN_TIMEOUT, unit: 'HOURS') {
                            // Note: Underlying org.apache DirectoryScanner used by cleanWs has a bug scanning where it misses files containing ".." so use rm -rf instead
                            // Issue: https://issues.jenkins.io/browse/JENKINS-64779
                            if (context.WORKSPACE != null && !context.WORKSPACE.isEmpty()) {
                                if (cleanWorkspaceAfter) {
                                    try {
                                        context.println "Cleaning workspace non-hidden files: ${context.WORKSPACE}/*"
                                        if (context.isUnix()) {
                                          // Linux/macOS cleanup
                                          context.sh(script: "rm -rf \"${context.WORKSPACE}\"/*")
                                        } else {
                                          // Cleanup Using Robocopy MS recommended solution
                                          // For Removing Files With Corrupted ACLs
                                          // https://learn.microsoft.com/en-us/troubleshoot/windows-server/backup-and-storage/cannot-delete-file-folder-on-ntfs-file-system
                                          context.bat """
                                            mkdir C:\\emptydir >NUL 2>&1
                                            robocopy C:\\emptydir "${context.WORKSPACE}" /MIR /R:0 /W:0 /NFL /NDL /NJH /NJS /NC /NS /NP >NUL 2>&1
                                            set RC=%ERRORLEVEL%
                                            if %RC% LEQ 3 ( exit /b 0 ) else ( exit /b %RC% )
                                            rmdir C:\\emptydir >NUL 2>&1
                                          """
                                        }
                                    } catch (e) {
                                        context.println "Warning: Failed to clean workspace non-hidden files ${e}"
                                    }

                                    // Clean remaining hidden files using cleanWs
                                    try {
                                        context.println 'Cleaning workspace hidden files using cleanWs: ' + context.WORKSPACE
                                        context.cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
                                    } catch (e) {
                                        context.println "Warning: Failed to clean ${e}"
                                    }
                                } else if (cleanWorkspaceBuildOutputAfter) {
                                    try {
                                      context.println 'Cleaning workspace build output files under ' + context.WORKSPACE
                                      batOrSh('rm -rf ' + context.WORKSPACE + '/workspace/build/src/build ' + context.WORKSPACE + '/workspace/target ' + context.WORKSPACE + '/workspace/build/devkit ' + context.WORKSPACE + '/workspace/build/straceOutput')
                                    } catch (e) {
                                        context.println "Warning: Failed to clean workspace build output files ${e}"
                                    }
                                }
                            } else {
                                context.println 'Warning: Unable to clean workspace as context.WORKSPACE is null/empty'
                            }
                        }
                    } catch (FlowInterruptedException e) {
                        // Set Github Commit Status
                        if (env.JOB_NAME.contains('pr-tester')) {
                            updateGithubCommitStatus('FAILED', 'Build FAILED')
                        }
                        throw new Exception("[ERROR] AIX clean workspace timeout (${buildTimeouts.AIX_CLEAN_TIMEOUT} HOURS) has been reached. Exiting...")
                    }
                }
}

def buildScriptsAssemble(
    cleanWorkspaceAfter,
    cleanWorkspaceBuildOutputAfter,
    buildConfigEnvVars
) {
    def assembleBuildArgs

    context.stage('assemble') {
      try {
        // This would ideally not be required but it's due to lack of UID mapping in windows containers
        if ( buildConfig.TARGET_OS == 'windows' && buildConfig.DOCKER_IMAGE) {
            def cygwin_workspace = context.WORKSPACE
            if ( !cygwin_workspace.startsWith("/cygdrive") ) {
                // Where cygwin_workspace is expected to be something like: C:/workspace/openjdk-build
                cygwin_workspace = "/cygdrive/" + cygwin_workspace.toLowerCase().charAt(0) + cygwin_workspace.substring(2)
            }
            context.bat('chmod -R a+rwX ' + cygwin_workspace + '/workspace/build/src/build/*')
        }
        // Restore signed JMODs
        context.unstash 'signed_jmods'
        // Convert IndividualBuildConfig to jenkins env variables
        context.withEnv(buildConfigEnvVars) {
            if (env.BUILD_ARGS != null && !env.BUILD_ARGS.isEmpty()) {
              assembleBuildArgs = env.BUILD_ARGS + ' --assemble-exploded-image'
            } else {
              assembleBuildArgs = '--assemble-exploded-image'
            }
            context.withEnv(['BUILD_ARGS=' + assembleBuildArgs]) {
                context.println '[CHECKOUT] Checking out to adoptium/temurin-build...'
                def repoHandler = new RepoHandler(USER_REMOTE_CONFIGS, ADOPT_DEFAULTS_JSON, buildConfig.CI_REF, buildConfig.BUILD_REF)
                repoHandler.checkoutAdoptBuild(context)
                if ( buildConfig.TARGET_OS == 'windows' && buildConfig.DOCKER_IMAGE ) {
                    context.bat(script: 'bash -c "git config --global safe.directory $(cygpath ' + '\$' + '{WORKSPACE})"')
                }
                printGitRepoInfo()
                context.println 'openjdk_build_pipeline.groovy: Assembling the exploded image'
                // Call make-adopt-build-farm.sh on windows/mac to create signed tarball
                try {
                    context.timeout(time: buildTimeouts.BUILD_JDK_TIMEOUT, unit: 'HOURS') {
                        context.println "openjdk_build_pipeline: calling MABF to assemble on win/mac JDK11+"
                        if ( !context.isUnix() && buildConfig.DOCKER_IMAGE ) {
                            // Running ls -l here generates the shortname links required by the
                            // build and create paths referenced in the config.status file
                            context.bat(script: 'ls -l /cygdrive/c "/cygdrive/c/Program Files (x86)" "/cygdrive/c/Program Files (x86)/Microsoft Visual Studio/2022" "/cygdrive/c/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Redist/MSVC" "/cygdrive/c/Program Files (x86)/Windows Kits/10/bin" "/cygdrive/c/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC" "/cygdrive/c/Program Files (x86)/Windows Kits/10/include" "/cygdrive/c/Program Files (x86)/Windows Kits/10/lib"')
                        }
                        batOrSh("bash ${ADOPT_DEFAULTS_JSON['scriptDirectories']['buildfarm']} --assemble-exploded-image")
                    }
                } catch (FlowInterruptedException e) {
                    // Set Github Commit Status
                    if (env.JOB_NAME.contains('pr-tester')) {
                        updateGithubCommitStatus('FAILED', 'Build FAILED')
                    }
                    throw new Exception("[ERROR] Build JDK timeout (${buildTimeouts.BUILD_JDK_TIMEOUT} HOURS) has been reached. Exiting...")
                }
            } // context.withEnv(assembleBuildargs)
        } // context.withEnv(buildConfigEnvVars)
        String versionOut
        if (buildConfig.BUILD_ARGS.contains('--cross-compile')) {
            context.println "[WARNING] Don't read faked version.txt on cross compiled build! Archiving early and running downstream job to retrieve java version..."
            versionOut = readCrossCompiledVersionString()
        } else {
            versionOut = context.readFile('workspace/target/metadata/version.txt')
        }
        versionInfo = parseVersionOutput(versionOut)
        writeMetadata(versionInfo, true)
        // Always archive any artifacts including failed make logs..
        try {
            context.timeout(time: buildTimeouts.BUILD_ARCHIVE_TIMEOUT, unit: 'HOURS') {
                // We have already archived cross compiled artifacts, so only archive the metadata files
                if (buildConfig.BUILD_ARGS.contains('--cross-compile')) {
                    context.println '[INFO] Archiving JSON Files...'
                    context.archiveArtifacts artifacts: 'workspace/target/*.json'
                } else {
                    context.archiveArtifacts artifacts: 'workspace/target/*'
                }
            }
        } catch (FlowInterruptedException e) {
            // Set Github Commit Status
            if (env.JOB_NAME.contains('pr-tester')) {
                updateGithubCommitStatus('FAILED', 'Build FAILED')
            }
            throw new Exception("[ERROR] Build archive timeout (${buildTimeouts.BUILD_ARCHIVE_TIMEOUT} HOURS) has been reached. Exiting...")
        }
      } finally {
        postBuildWSclean(cleanWorkspaceAfter, cleanWorkspaceBuildOutputAfter)
      }
    } // context.stage('assemble')
} // End of buildScriptsAssemble() 1643-1765

/*
    Executed on a build node, the function checks out the repository and executes the build via ./make-adopt-build-farm.sh
    Once the build completes, it will calculate its version output, commit the first metadata writeout, and archive the build results.
    Running in downstream job jdk-*-*-* build stage, called by build()
    */

    def buildScripts(
        cleanWorkspace,
        cleanWorkspaceAfter,
        cleanWorkspaceBuildOutputAfter,
        useAdoptShellScripts,
        enableSigner,
        buildConfigEnvVars
    ) {
        // Create the repo handler with the user's defaults to ensure a temurin-build checkout is not null
        // Pass actual ADOPT_DEFAULTS_JSON, and optional buildConfig CI and BUILD branch/tag overrides,
        // so that RepoHandler checks out the desired repo correctly
        def repoHandler = new RepoHandler(USER_REMOTE_CONFIGS, ADOPT_DEFAULTS_JSON, buildConfig.CI_REF, buildConfig.BUILD_REF)
        repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON['defaultsUrl'])
        return context.stage('build') {
            context.println 'USER_REMOTE_CONFIGS: '
            context.println JsonOutput.toJson(USER_REMOTE_CONFIGS)
            context.println 'DEFAULTS_JSON: '
            context.println JsonOutput.toJson(DEFAULTS_JSON)
            context.println 'ADOPT_DEFAULTS_JSON: '
            context.println JsonOutput.toJson(ADOPT_DEFAULTS_JSON)
            context.println 'Optional branch/tag/commitSHA overrides:'
            context.println '    buildConfig.CI_REF: ' + buildConfig.CI_REF
            context.println '    buildConfig.BUILD_REF: ' + buildConfig.BUILD_REF
            context.println '    buildConfig.HELPER_REF: ' + buildConfig.HELPER_REF

            def build_path
            def openjdk_build_dir
            def openjdk_build_dir_arg

            // Build as default within OpenJDK src tree, necessary for Windows reproducible builds, due to relative paths
            build_path = 'workspace/build/src/build'
            openjdk_build_dir =  context.WORKSPACE + '/' + build_path
            openjdk_build_dir_arg = ""

            if (cleanWorkspace) {
                try {
                    try {
                        context.timeout(time: buildTimeouts.NODE_CLEAN_TIMEOUT, unit: 'HOURS') {
                            // Clean non-hidden files first
                            // Note: Underlying org.apache DirectoryScanner used by cleanWs has a bug scanning where it misses files containing ".." so use rm -rf instead
                            // Issue: https://issues.jenkins.io/browse/JENKINS-64779
                            if (context.WORKSPACE != null && !context.WORKSPACE.isEmpty()) {
                                context.println 'Cleaning workspace non-hidden files: ' + context.WORKSPACE + '/*'
                                batOrSh(script: 'rm -rf ' + context.WORKSPACE + '/*')
                            } else {
                                context.println 'Warning: Unable to clean workspace as context.WORKSPACE is null/empty'
                            }

                            // Clean remaining hidden files using cleanWs
                            try {
                                context.println 'Cleaning workspace hidden files using cleanWs: ' + context.WORKSPACE
                                context.cleanWs notFailBuild: true, disableDeferredWipeout: true, deleteDirs: true
                            } catch (e) {
                                context.println "Failed to clean ${e}"
                            }
                        }
                    } catch (FlowInterruptedException e) {
                        throw new Exception("[ERROR] Node Clean workspace timeout (${buildTimeouts.NODE_CLEAN_TIMEOUT} HOURS) has been reached. Exiting...")
                    }
                } catch (e) {
                    context.println "[WARNING] Failed to clean workspace: ${e}"
                }
            }

            // Always clean any previous "openjdk_build_dir" output, possibly from any previous aborted build..
            try {
                try {
                    context.timeout(time: buildTimeouts.NODE_CLEAN_TIMEOUT, unit: 'HOURS') {
                        if (context.WORKSPACE != null && !context.WORKSPACE.isEmpty()) {
                            context.println 'Removing workspace openjdk build directory: ' + openjdk_build_dir
                            batOrSh('rm -rf ' + openjdk_build_dir)
                        } else {
                            context.println 'Warning: Unable to remove workspace openjdk build directory as context.WORKSPACE is null/empty'
                        }
                    }
                } catch (FlowInterruptedException e) {
                    throw new Exception("[ERROR] Remove workspace openjdk build directory timeout (${buildTimeouts.NODE_CLEAN_TIMEOUT} HOURS) has been reached. Exiting...")
                }
            } catch (e) {
                context.println "[WARNING] Failed to remove workspace openjdk build directory: ${e}"
            }

            try {
                context.timeout(time: buildTimeouts.NODE_CHECKOUT_TIMEOUT, unit: 'HOURS') {
                    if (useAdoptShellScripts) {
                        repoHandler.checkoutAdoptPipelines(context)
                    } else {
                        repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON)
                        repoHandler.checkoutUserPipelines(context)
                    }

                    // Perform a git clean outside of checkout to avoid the Jenkins enforced 10 minute timeout
                    // https://github.com/adoptium/infrastucture/issues/1553

                    if ( buildConfig.TARGET_OS == 'windows' && buildConfig.DOCKER_IMAGE ) {
                        context.bat(script: 'bash -c "git config --global safe.directory $(cygpath ' + '\$' + '{WORKSPACE})"')
                    }
                    batOrSh('git clean -fdx')
                    printGitRepoInfo()
                }
            } catch (FlowInterruptedException e) {
                throw new Exception("[ERROR] Node checkout workspace timeout (${buildTimeouts.NODE_CHECKOUT_TIMEOUT} HOURS) has been reached. Exiting...")
            }

            try {
                // Convert IndividualBuildConfig to jenkins env variables
                // Execute build
                context.withEnv(buildConfigEnvVars) {
                    try {
                        context.timeout(time: buildTimeouts.BUILD_JDK_TIMEOUT, unit: 'HOURS') {
                            // Set Github Commit Status
                            if (env.JOB_NAME.contains('pr-tester')) {
                                updateGithubCommitStatus('PENDING', 'Build Started')
                            }
                            if (useAdoptShellScripts) {
                                context.println '[CHECKOUT] Checking out to adoptium/temurin-build...'
                                repoHandler.checkoutAdoptBuild(context)
                                printGitRepoInfo()
                                if ((buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') && buildConfig.JAVA_TO_BUILD != 'jdk8u' && enableSigner) {
                                    context.println "Generating exploded build" // , sign JMODS, and assemble build, for platform ${buildConfig.TARGET_OS} version ${buildConfig.JAVA_TO_BUILD}"
                                    def signBuildArgs // Build args for make-adopt-build-farm.sh
                                    if (env.BUILD_ARGS != null && !env.BUILD_ARGS.isEmpty()) {
                                        signBuildArgs = env.BUILD_ARGS + ' --make-exploded-image' + openjdk_build_dir_arg
                                    } else {
                                        signBuildArgs = '--make-exploded-image' + openjdk_build_dir_arg
                                    }
                                    context.withEnv(['BUILD_ARGS=' + signBuildArgs]) {
                                        context.println 'Building an exploded image for signing'
                                        // Call make-adopt-build-farm.sh to do initial windows/mac build
                                        context.println "openjdk_build_pipeline: Calling MABF on win/mac to build exploded image"
                                        batOrSh("bash ./${ADOPT_DEFAULTS_JSON['scriptDirectories']['buildfarm']}")
                                    }
                                    def base_path = build_path
                                    if (openjdk_build_dir_arg == "") {
                                        // If not using a custom openjdk build dir, then query what autoconf created as the build sub-folder
                                        if ( context.isUnix() ) {
                                            base_path = context.sh(script: "ls -d ${build_path}/*", returnStdout:true).trim()
                                        } else {
                                            base_path = context.bat(script: "@ls -d ${build_path}/*", returnStdout:true).trim()
                                        }
                                    }
                                    context.println "base_path for jmod signing = ${base_path}."
                                    def files_to_sign_list = getEclipseSigningFileList(base_path)
                                    context.stash name: 'jmods', includes: "${files_to_sign_list}"

                                    // eclipse-codesign and assemble sections were inlined here before
                                    // https://github.com/adoptium/ci-jenkins-pipelines/pull/1117

                                } else { // Not Windows/Mac JDK11+ (i.e. doesn't require internal signing)
                                    def buildArgs
                                    if (env.BUILD_ARGS != null && !env.BUILD_ARGS.isEmpty()) {
                                        buildArgs = env.BUILD_ARGS + openjdk_build_dir_arg
                                    } else {
                                        buildArgs = openjdk_build_dir_arg
                                    }
                                    context.withEnv(['BUILD_ARGS=' + buildArgs]) {
                                        context.println "openjdk_build_pipeline: Calling MABF when not win/mac JDK11+ to do single-pass build and UASS=false"
                                        batOrSh("bash ./${ADOPT_DEFAULTS_JSON['scriptDirectories']['buildfarm']}")
                                    }
                                }
                                context.println '[CHECKOUT] Reverting pre-build adoptium/temurin-build checkout...'
                                // Special case for the pr tester as checking out to the user's pipelines doesn't play nicely
                                if (env.JOB_NAME.contains('pr-tester')) {
                                    context.checkout context.scm
                                } else {
                                    repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON)
                                    repoHandler.checkoutUserPipelines(context)
                                }
                                printGitRepoInfo()
                            } else { // USE_ADOPT_SHELL_SCRIPTS == false
                                context.println "[CHECKOUT] Checking out to the user's temurin-build..."
                                repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON)
                                repoHandler.checkoutUserBuild(context)
                                printGitRepoInfo()
                                def buildArgs
                                if (env.BUILD_ARGS != null && !env.BUILD_ARGS.isEmpty()) {
                                    buildArgs = env.BUILD_ARGS + openjdk_build_dir_arg
                                } else {
                                    buildArgs = openjdk_build_dir_arg
                                }
                                context.withEnv(['BUILD_ARGS=' + buildArgs]) {
                                    context.println "openjdk_build_pipeline: calling MABF to do single pass build when USE_ADOPT_SHELL_SCRIPTS is false"
                                    batOrSh("bash ./${DEFAULTS_JSON['scriptDirectories']['buildfarm']}")
                                }
                                context.println '[CHECKOUT] Reverting pre-build user temurin-build checkout...'
                                repoHandler.checkoutUserPipelines(context)
                                printGitRepoInfo()
                            }
                        }
                    } catch (FlowInterruptedException e) {
                        // Set Github Commit Status
                        if (env.JOB_NAME.contains('pr-tester')) {
                            updateGithubCommitStatus('FAILED', 'Build FAILED')
                        }
                        throw new Exception("[ERROR] Build JDK timeout (${buildTimeouts.BUILD_JDK_TIMEOUT} HOURS) has been reached. Exiting...")
                    }
                    // TODO: Make the "internal signing/assembly" part independent of
                    // ENABLE_SIGNER so that this platform-specific logic is not required
                    if ((buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') && buildConfig.JAVA_TO_BUILD != 'jdk8u' && enableSigner) {
                        context.println "openjdk_build_pipeline: Internal signing phase required - skipping metadata reading"
                    } else {
                        // Run a downstream job on riscv machine that returns the java version. Otherwise, just read the version.txt
                        String versionOut
                            if (buildConfig.BUILD_ARGS.contains('--cross-compile')) {
                            context.println "[WARNING] Don't read faked version.txt on cross compiled build! Archiving early and running downstream job to retrieve java version..."
                            versionOut = readCrossCompiledVersionString()
                        } else {
                            versionOut = context.readFile('workspace/target/metadata/version.txt')
                        }
                        versionInfo = parseVersionOutput(versionOut)
                    }
                }
                if (!((buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') && buildConfig.JAVA_TO_BUILD != 'jdk8u' && enableSigner)) {
                    writeMetadata(versionInfo, true)
                } else {
                    context.println "Skipping writing incomplete metadata for now - will be done in the assemble phase instead"
                }

            } finally {
                // Archive any artifacts including failed make logs, unless doing internal
                // signing where we will perform this step after the assemble phase
                if (!((buildConfig.TARGET_OS == 'mac' || buildConfig.TARGET_OS == 'windows') && buildConfig.JAVA_TO_BUILD != 'jdk8u' && enableSigner)) {
                   try {
                       context.timeout(time: buildTimeouts.BUILD_ARCHIVE_TIMEOUT, unit: 'HOURS') {
                          // We have already archived cross compiled artifacts, so only archive the metadata files
                          if (buildConfig.BUILD_ARGS.contains('--cross-compile')) {
                              context.println '[INFO] Archiving JSON Files...'
                              context.archiveArtifacts artifacts: 'workspace/target/*.json'
                          } else {
                              context.archiveArtifacts artifacts: 'workspace/target/*'
                          }
                       }
                   } catch (FlowInterruptedException e) {
                       // Set Github Commit Status
                       if (env.JOB_NAME.contains('pr-tester')) {
                           updateGithubCommitStatus('FAILED', 'Build FAILED')
                       }
                       throw new Exception("[ERROR] Build archive timeout (${buildTimeouts.BUILD_ARCHIVE_TIMEOUT} HOURS) has been reached. Exiting...")
                   }
                   postBuildWSclean(cleanWorkspaceAfter, cleanWorkspaceBuildOutputAfter)
                   // Set Github Commit Status
                   if (env.JOB_NAME.contains('pr-tester')) {
                       updateGithubCommitStatus('SUCCESS', 'Build PASSED')
                   }
               }
            }
        }
    }

    /*
    Pulls in and applies the activeNodeTimeout parameter.
    The function will use the jenkins helper nodeIsOnline lib to check once a minute if a node with the specified label has come online.
    If it doesn't find one or the timeout is set to 0 (default), it'll crash out. Otherwise, it'll return and jump onto the node.
    */
    def waitForANodeToBecomeActive(def label) {
        String helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
        def NodeHelper = context.library(identifier: "openjdk-jenkins-helper@${helperRef}").NodeHelper

        // If label contains mac skip waiting for node to become active as we use Orka
        if (label.contains('mac')) {
            return
        }

        // A node with the requested label is ready to go
        if (NodeHelper.nodeIsOnline(label)) {
            return
        }

        context.println('No active node matches this label: ' + label)

        // Import activeNodeTimeout param
        int activeNodeTimeout = 0
        if (buildConfig.ACTIVE_NODE_TIMEOUT.isInteger()) {
            activeNodeTimeout = buildConfig.ACTIVE_NODE_TIMEOUT as Integer
        }

        if (activeNodeTimeout > 0) {
            context.println('Will check again periodically until a node labelled ' + label + ' comes online, or ' + buildConfig.ACTIVE_NODE_TIMEOUT + ' minutes (ACTIVE_NODE_TIMEOUT) has passed.')
            int x = 0
            while (x < activeNodeTimeout) {
                context.sleep(time: 1, unit: 'MINUTES')
                if (NodeHelper.nodeIsOnline(label)) {
                    context.println('A node which matches this label is now active: ' + label)
                    return
                }
                x++
            }
            throw new Exception('No node matching this label became active prior to the timeout: ' + label)
        } else {
            throw new Exception('As the timeout value is set to 0, we will not wait for a node to become active.')
        }
    }

    /*
        this function should only be used in pr-tester
    */
    def updateGithubCommitStatus(STATE, MESSAGE) {
        // workaround https://issues.jenkins-ci.org/browse/JENKINS-38674
        // get repourl from job's DEFAULTS_JSON  points to upstream repo
        String repoUrl = DEFAULTS_JSON['repository']['pipeline_url'] // USER_REMOTE_CONFIGS['remotes']['url']
        // get branch/commit SHA1 from job's USER_REMOTE_CONFIGS which is the commits from PR
        Map paramUserRemoteConfigs = new JsonSlurper().parseText(context.USER_REMOTE_CONFIGS)
        String commitSha = paramUserRemoteConfigs['branch']

        String shortJobName = env.JOB_NAME.split('/').last()

        context.println 'Setting GitHub Checks Status:'
        context.println "REPO URL: ${repoUrl}"
        context.println "COMMIT SHA: ${commitSha}"
        context.println "STATE: ${STATE}"
        context.println "MESSAGE: ${MESSAGE}"
        context.println "JOB NAME: ${shortJobName}"

        context.step([
            $class: 'GitHubCommitStatusSetter',
            reposSource: [$class: 'ManuallyEnteredRepositorySource', url: repoUrl],
            commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: commitSha],
            contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: shortJobName],
            errorHandlers: [[$class: 'ChangingBuildStatusErrorHandler', result: 'UNSTABLE']],
            statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: MESSAGE, state: STATE]] ]
        ])
    }

    def addNodeToBuildDescription() {
        // Append to existing build description if not null
        def tmpDesc = (context.currentBuild.description) ? context.currentBuild.description + '<br>' : ''
        context.currentBuild.description = tmpDesc + "<a href=${context.JENKINS_URL}computer/${context.NODE_NAME}>${context.NODE_NAME}</a>"
    }

    /*
    This method validates all SBOMs produced by this build.
    */
    def validateSbom() {
        String jobName = "sbom_validator_job"

        try {
            context.println 'Validating SBOM/s'
            context.stage('validate SBOM') {
                // Check sbom validation job exists.
                String helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
                def JobHelper = context.library(identifier: "openjdk-jenkins-helper@${helperRef}").JobHelper
                if (!JobHelper.jobIsRunnable(jobName as String)) {
                    throw new Exception("[ERROR] Jenkins job ${jobName} could not be found.");
                }

                // Gather parameters.
                String jdk_Version = getJavaVersionNumber() as String
                String source_tag = ""
                if (!buildConfig.SCM_REF.isEmpty()){
                    source_tag = buildConfig.SCM_REF
                }

                // Launch job to validate SBOMs
                def validationJob = context.build job: jobName,
                    propagate: false,
                    parameters: [
                            context.string(name: 'VERSION', value: "${jdk_Version}"),
                            context.string(name: 'TAG', value: "${source_tag}"),
                            context.string(name: 'UPSTREAM_JOB_NUMBER', value: "${env.BUILD_NUMBER}"),
                            context.string(name: 'UPSTREAM_JOB_NAME', value: "${env.JOB_NAME}"),
                            context.string(name: 'UPSTREAM_DIR', value: "workspace/target")
                    ]
                currentBuild.result = validationJob.getResult()
                setStageResult("validate sbom", validationJob.getResult())
                return validationJob.getResult()
            }
        } catch (e) {
            context.println("Failed to validate ${buildConfig.TARGET_OS} SBOMs ${e}")
            currentBuild.result = 'FAILURE'
            setStageResult("validate sbom", 'FAILURE')
        }
    }

    /*
    Main function. This is what is executed remotely via the helper file kick_off_build.groovy, which is in turn executed by the downstream jobs.
    Running in downstream build job jdk-*-*-* called by kick_off_build.groovy
    */
    def build() {
        context.timestamps {
            try {
                context.println 'Build config (BUILD_CONFIGURAION):'
                context.println buildConfig.toJson()

                def filename = determineFileName()

                context.println "Executing tests: ${buildConfig.TEST_LIST}"
                context.println "Build num: ${env.BUILD_NUMBER}"
                context.println "File name: ${filename}"

                def enableReproducibleCompare = Boolean.valueOf(buildConfig.ENABLE_REPRODUCIBLE_COMPARE)
                def enableTests = Boolean.valueOf(buildConfig.ENABLE_TESTS)
                def enableInstallers = Boolean.valueOf(buildConfig.ENABLE_INSTALLERS)
                def enableSigner = Boolean.valueOf(buildConfig.ENABLE_SIGNER)
                def enableTCK = Boolean.valueOf(buildConfig.RELEASE) || Boolean.valueOf(buildConfig.WEEKLY)
                if ('jdk'.equalsIgnoreCase(buildConfig.JAVA_TO_BUILD.trim())) { enableTCK = false }
                def useAdoptShellScripts = Boolean.valueOf(buildConfig.USE_ADOPT_SHELL_SCRIPTS)
                def cleanWorkspace = Boolean.valueOf(buildConfig.CLEAN_WORKSPACE)
                def cleanWorkspaceAfter = Boolean.valueOf(buildConfig.CLEAN_WORKSPACE_AFTER)
                def cleanWorkspaceBuildOutputAfter = Boolean.valueOf(buildConfig.CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER)
                // Get branch/tag of temurin-build, ci-jenkins-pipeline and jenkins-helper repo from BUILD_CONFIGURATION or defaultsJson
                def helperRef = buildConfig.HELPER_REF ?: DEFAULTS_JSON['repository']['helper_ref']
                def nonDockerNodeName = ''

                // Convert IndividualBuildConfig to jenkins env variables
                List<String> envVars = buildConfig.toEnvVars()
                envVars.add("FILENAME=${filename}" as String)
                // Use BUILD_REF override if specified
                def adoptBranch = buildConfig.BUILD_REF ?: ADOPT_DEFAULTS_JSON['repository']['build_branch']
                // Add platform config path so it can be used if the user doesn't have one
                def splitAdoptUrl = ((String)ADOPT_DEFAULTS_JSON['repository']['build_url']) - ('.git').split('/')
                // e.g. https://github.com/adoptium/temurin-build.git will produce adoptium/temurin-build
                String userOrgRepo = "${splitAdoptUrl[splitAdoptUrl.size() - 2]}/${splitAdoptUrl[splitAdoptUrl.size() - 1]}"
                // e.g. adoptium/temurin-build/master/build-farm/platform-specific-configurations
                envVars.add("ADOPT_PLATFORM_CONFIG_LOCATION=${userOrgRepo}/${adoptBranch}/${ADOPT_DEFAULTS_JSON['configDirectories']['platform']}" as String)
                def internalSigningRequired = (buildConfig.TARGET_OS == 'windows' || buildConfig.TARGET_OS == 'mac')

                context.stage('queue') {
                    /* This loads the library containing two Helper classes, and causes them to be
                    imported/updated from their repo. Without the library being imported here, runTests method will fail to execute the post-build test jobs for reasons unknown.*/
                    context.library(identifier: "openjdk-jenkins-helper@${helperRef}")

                    // Set Github Commit Status
                    if (env.JOB_NAME.contains('pr-tester')) {
                        context.node('worker') {
                            updateGithubCommitStatus('PENDING', 'Pending')
                        }
                    }
                    def workspace
                    if (buildConfig.TARGET_OS == 'windows') {
                       workspace = 'C:/workspace/openjdk-build/'
                    }
                    if (buildConfig.DOCKER_IMAGE) {
                        context.println "openjdk_build_pipeline: preparing to use docker image"
                        // Docker build environment
                        def label = buildConfig.NODE_LABEL + '&&dockerBuild'
                        if (buildConfig.DOCKER_NODE) {
                            label = buildConfig.NODE_LABEL + '&&' + "$buildConfig.DOCKER_NODE"
                        }

                        if (buildConfig.CODEBUILD) {
                            label = 'codebuild'
                        }


                        context.println "[NODE SHIFT] MOVING INTO DOCKER NODE MATCHING LABELNAME ${label}..."
                        if ( ! ( "${buildConfig.DOCKER_IMAGE}" ==~ /^[A-Za-z0-9\/\.\-_:@]*$/ ) ||
                             ! ( "${buildConfig.DOCKER_ARGS}"  ==~ /^[A-Za-z0-9\/\.\-_=\ ]*$/ ) ) {
                             throw new Exception("[ERROR] Dubious characters in DOCKER* image or parameters: ${buildConfig.DOCKER_IMAGE} ${buildConfig.DOCKER_ARGS} - aborting");
                        }
                        context.node(label) {
                            addNodeToBuildDescription()
                            // Cannot clean workspace from inside docker container
                            if ( buildConfig.TARGET_OS == 'windows' && buildConfig.DOCKER_IMAGE ) {
                                context.ws(workspace) {
                                    context.bat("rm -rf " + context.WORKSPACE + "/cyclonedx-lib " +
                                                            context.WORKSPACE + "/security")
                                }
                            }
                            if (cleanWorkspace) {
                                try {
                                    context.timeout(time: buildTimeouts.CONTROLLER_CLEAN_TIMEOUT, unit: 'HOURS') {
                                        // Cannot clean workspace from inside docker container
                                        if (cleanWorkspace) {
                                            try {
                                                context.cleanWs notFailBuild: true
                                            } catch (e) {
                                                context.println "Warning: Failed to clean ${e}"
                                            }
                                            cleanWorkspace = false
                                        }
                                        // For Windows build also clean alternative(shorter path length) workspace
                                        if ( buildConfig.TARGET_OS == 'windows' ) {
                                            context.ws(workspace) {
                                                try {
                                                    context.println "Windows build cleaning" + context.WORKSPACE
                                                    context.cleanWs notFailBuild: true
                                                } catch (e) {
                                                    context.println "Warning: Failed to clean ${e}"
                                                }
                                            }
                                        }
                                    }
                                } catch (FlowInterruptedException e) {
                                    throw new Exception("[ERROR] Controller clean workspace timeout (${buildTimeouts.CONTROLLER_CLEAN_TIMEOUT} HOURS) has been reached. Exiting...")
                                }
                            }

                            // Target docker image to use, this may get aliased to a target tag using docker tag as target cannot contain a digest
                            def docker_image_target = buildConfig.DOCKER_IMAGE

                            if (!("${docker_image_target}".contains('rhel'))) {
                                // Pull the docker image from DockerHub
                                try {
                                    context.timeout(time: buildTimeouts.DOCKER_PULL_TIMEOUT, unit: 'HOURS') {
                                        if (buildConfig.DOCKER_CREDENTIAL) {
                                            context.docker.withRegistry(buildConfig.DOCKER_REGISTRY, buildConfig.DOCKER_CREDENTIAL) {
                                                if (buildConfig.DOCKER_ARGS) {
                                                    context.sh(script: "docker pull ${docker_image_target} ${buildConfig.DOCKER_ARGS}")
                                                } else {
                                                    context.docker.image(docker_image_target).pull()
                                                }
                                            }
                                            def imageParts = docker_image_target.tokenize('@')
                                            def imageName = imageParts[0]
                                            def imageDigest = imageParts.size() > 1 ? imageParts[1] : "latest"
                                            def long_docker_image_name = context.sh(script: "docker image ls --digests| grep ${imageName} | grep ${imageDigest} | head -n1 | awk '{print \$1}'", returnStdout:true).trim()
                                            def source_tag
                                            if (docker_image_target.contains('@')) {
                                                source_tag = "@${imageDigest}"
                                                // docker_image target contains a digest(sha) which cannot be a target tag
                                                // create a new target tag name based on syntax: <imageName>_<sha256>
                                                docker_image_target = imageName + "_" + imageDigest.replaceAll(":","_")
                                                context.println "Mapped ${buildConfig.DOCKER_IMAGE} to target tag ${docker_image_target}, as it contains a digest"
                                            } else {
                                                // ":latest"
                                                source_tag = ":${imageDigest}"
                                            }
                                            context.sh(script: "docker tag '${long_docker_image_name}${source_tag}' '${docker_image_target}'", returnStdout:false)
                                        } else {
                                            if (buildConfig.DOCKER_ARGS) {
                                                context.sh(script: "docker pull ${docker_image_target} ${buildConfig.DOCKER_ARGS}")
                                            } else {
                                                context.docker.image(docker_image_target).pull()
                                            }
                                        }
                                    }
                                } catch (FlowInterruptedException e) {
                                    throw new Exception("[ERROR] Controller docker image pull timeout (${buildTimeouts.DOCKER_PULL_TIMEOUT} HOURS) has been reached. Exiting...")
                                }
                            }
                            // Store the pulled docker image digest as 'buildinfo'
                            if ( buildConfig.TARGET_OS == 'windows' && docker_image_target ) {
                                dockerImageDigest = context.sh(script: "docker inspect --format={{.Id}} ${docker_image_target} | /bin/cut -d: -f2", returnStdout:true)
                            } else {
                                dockerImageDigest = context.sh(script: "docker inspect --format='{{.RepoDigests}}' ${docker_image_target}", returnStdout:true)
                            }
                            context.println "Target docker image digest = ${dockerImageDigest}"

                            // Use our dockerfile if DOCKER_FILE is defined
                            if (buildConfig.DOCKER_FILE) {
                                try {
                                    context.timeout(time: buildTimeouts.DOCKER_CHECKOUT_TIMEOUT, unit: 'HOURS') {
                                        def repoHandler = new RepoHandler(USER_REMOTE_CONFIGS, ADOPT_DEFAULTS_JSON, buildConfig.CI_REF, buildConfig.BUILD_REF)
                                        repoHandler.setUserDefaultsJson(context, DEFAULTS_JSON)
                                        if (useAdoptShellScripts) {
                                            repoHandler.checkoutAdoptPipelines(context)
                                        } else {
                                            repoHandler.checkoutUserPipelines(context)
                                        }

                                        // Perform a git clean outside of checkout to avoid the Jenkins enforced 10 minute timeout
                                        // https://github.com/adoptium/infrastucture/issues/1553
                                        context.sh(script: 'git clean -fdx')

                                        printGitRepoInfo()
                                    }
                                } catch (FlowInterruptedException e) {
                                    throw new Exception("[ERROR] Controller docker file scm checkout timeout (${buildTimeouts.DOCKER_CHECKOUT_TIMEOUT} HOURS) has been reached. Exiting...")
                                }

                                context.println "openjdk_build_pipeline: building in docker image from docker file " + buildConfig.DOCKER_FILE
                                context.docker.build("build-image", "--build-arg image=${docker_image_target} -f ${buildConfig.DOCKER_FILE} .").inside(buildConfig.DOCKER_ARGS) {
                                    buildScripts(
                                        cleanWorkspace,
                                        cleanWorkspaceAfter,
                                        cleanWorkspaceBuildOutputAfter,
                                        useAdoptShellScripts,
                                        enableSigner,
                                        envVars
                                    )
                                }
                            } else {
                                dockerImageDigest = dockerImageDigest.replaceAll("\\[", "").replaceAll("\\]", "")
                                String dockerRunArg="-e \"BUILDIMAGESHA=$dockerImageDigest\" --init"

                                // Are we running podman in Docker CLI Emulation mode?
                                def isPodman = context.sh(script: "docker --version | grep podman", returnStatus:true)
                                if (isPodman == 0) {
                                    // Note: --userns was introduced in podman 4.3.0
                                    // Add uid and gid userns mapping required for podman
                                    dockerRunArg += " --userns keep-id:uid=1002,gid=1003"
                                }
                                if (buildConfig.TARGET_OS == 'windows') {
                                    context.println "openjdk_build_pipeline: running exploded build in docker on Windows"
                                    context.echo("Switched to using non-default workspace path ${workspace}")
                                    context.println "openjdk_build_pipeline: building in windows docker image " + docker_image_target
                                    context.ws(workspace) {
                                        context.docker.image(docker_image_target).inside(buildConfig.DOCKER_ARGS+" "+dockerRunArg) {
                                            buildScripts(
                                                cleanWorkspace,
                                                cleanWorkspaceAfter,
                                                cleanWorkspaceBuildOutputAfter,
                                                useAdoptShellScripts,
                                                enableSigner,
                                                envVars
                                            )
                                        }
                                    }
                                } else {
                                    context.println "openjdk_build_pipeline: running initial build in docker on non-windows with image " + docker_image_target
                                    context.docker.image(docker_image_target).inside(buildConfig.DOCKER_ARGS+" "+dockerRunArg) {
                                        buildScripts(
                                            cleanWorkspace,
                                            cleanWorkspaceAfter,
                                            cleanWorkspaceBuildOutputAfter,
                                            useAdoptShellScripts,
                                            enableSigner,
                                            envVars
                                        )
                                    }
                                }
                                // Is thre potential for not enabling the signer on jdk8u instead of having this clause?
                                if ( enableSigner && internalSigningRequired && buildConfig.JAVA_TO_BUILD != 'jdk8u' ) {
                                    context.println "openjdk_build_pipeline: running eclipse signing phase"
                                    buildScriptsEclipseSigner()
                                    context.ws(workspace) {
                                        context.println "Signing with non-default workspace location ${workspace}"
                                        context.println "openjdk_build_pipeline: running assemble phase (invocation 1)"
                                            context.docker.image(docker_image_target).inside(buildConfig.DOCKER_ARGS+" "+dockerRunArg) {
                                            buildScriptsAssemble(
                                                cleanWorkspaceAfter,
                                                cleanWorkspaceBuildOutputAfter,
                                                envVars
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        context.println "[NODE SHIFT] OUT OF DOCKER NODE (LABELNAME ${label}!)"

                    // Build the jdk outside of docker container...
                    } else {
                        context.println "openjdk_build_pipeline: running build without docker"
                        waitForANodeToBecomeActive(buildConfig.NODE_LABEL)
                        context.println "openjdk_build_pipeline: [NODE SHIFT] MOVING INTO NON-DOCKER JENKINS NODE MATCHING LABELNAME ${buildConfig.NODE_LABEL}..."
                        context.node(buildConfig.NODE_LABEL) {
                            addNodeToBuildDescription()
                            nonDockerNodeName = context.NODE_NAME
                            // This is to avoid windows path length issues.
                            context.echo("checking ${buildConfig.TARGET_OS}")
                            if (buildConfig.TARGET_OS == 'windows') {
                                // See https://github.com/adoptium/infrastucture/issues/1284#issuecomment-621909378 for justification of the below path
                                if (env.CYGWIN_WORKSPACE) {
                                    workspace = env.CYGWIN_WORKSPACE
                                }
                                context.echo("Switched to using non-default workspace path ${workspace}")
                                context.println "openjdk_build_pipeline: running build without docker on windows"
                                context.ws(workspace) {
                                    buildScripts(
                                        cleanWorkspace,
                                        cleanWorkspaceAfter,
                                        cleanWorkspaceBuildOutputAfter,
                                        useAdoptShellScripts,
                                        enableSigner,
                                        envVars
                                    )
                                    if ( enableSigner && internalSigningRequired && buildConfig.JAVA_TO_BUILD != 'jdk8u' ) {
                                        buildScriptsEclipseSigner()
                                        context.println "openjdk_build_pipeline: running assemble phase (invocation 2)"
                                        buildScriptsAssemble(
                                            cleanWorkspaceAfter,
                                            cleanWorkspaceBuildOutputAfter,
                                            envVars
                                        )
                                    }
                                }
                            } else { // Non-windows, non-docker
                                context.println "openjdk_build_pipeline: running build without docker on non-windows platform"
                                buildScripts(
                                    cleanWorkspace,
                                    cleanWorkspaceAfter,
                                    cleanWorkspaceBuildOutputAfter,
                                    useAdoptShellScripts,
                                    enableSigner,
                                    envVars
                                )
                                if ( enableSigner && internalSigningRequired && buildConfig.JAVA_TO_BUILD != 'jdk8u' ) {
                                    buildScriptsEclipseSigner()
                                    context.println "openjdk_build_pipeline: running assemble phase (invocation 3)"
                                    buildScriptsAssemble(
                                        cleanWorkspaceAfter,
                                        cleanWorkspaceBuildOutputAfter,
                                        envVars
                                    )
                                }
                            }
                        }
                        context.println "[NODE SHIFT] OUT OF JENKINS NODE (LABELNAME ${buildConfig.NODE_LABEL}!)"
                    }
                }

                // Sign and archive jobs if needed
                if (enableSigner) {
                    try {
                        // Sign job timeout managed by Jenkins job config
                        context.println "openjdk_build_pipeline: executing signing phase"
                        sign(versionInfo)
                    } catch (FlowInterruptedException e) {
                        throw new Exception("[ERROR] Sign job timeout (${buildTimeouts.SIGN_JOB_TIMEOUT} HOURS) has been reached OR the downstream sign job failed. Exiting...")
                    }
                }

                //buildInstaller if needed
                if (enableInstallers) {
                    try {
                        // Installer job timeout managed by Jenkins job config
                        context.println "openjdk_build_pipeline: building installers"
                        buildInstaller(versionInfo)
                        if ( enableSigner) {
                            signInstaller(versionInfo)
                        }
                    } catch (FlowInterruptedException e) {
                        currentBuild.result = 'FAILURE'
                        throw new Exception("[ERROR] Installer job timeout (${buildTimeouts.INSTALLER_JOBS_TIMEOUT} HOURS) has been reached OR the downstream installer job failed. Exiting...")
                    }
                }
                if (!env.JOB_NAME.contains('pr-tester') && buildConfig.VARIANT == 'temurin' && enableSigner) {
                    try {
                        context.println "openjdk_build_pipeline: Running GPG signing process"
                        if (buildConfig.BUILD_ARGS.contains('--create-sbom')) {
                            jsfSignSBOM()
                        }
                        gpgSign()

                    } catch (Exception e) {
                        context.println(e.message)
                        currentBuild.result = 'FAILURE'
                    }
                }

                if (!env.JOB_NAME.contains('pr-tester')) { // pr-tester does not sign the binaries
                    // Verify Windows and Mac Signing for Temurin
                    if (buildConfig.VARIANT == 'temurin' && enableSigner) {
                        try {
                            context.println "openjdk_build_pipeline: Verifying signing"
                            verifySigning()
                        } catch (Exception e) {
                            context.println(e.message)
                            currentBuild.result = 'FAILURE'
                        }
                    }
                }

                // Validate the SBOM.
                if (buildConfig.BUILD_ARGS.contains('--create-sbom')) {
                    try {
                        if (validateSbom() == 'SUCCESS') {
                            context.println "openjdk_build_pipeline: SBOMs created by this build passed validation."
                        } else {
                            context.println('[ERROR] SBOMs created by this build failed validation.')
                            currentBuild.result = 'FAILURE'
                        }
                    } catch (Exception e) {
                        context.println(e.message)
                        currentBuild.result = 'FAILURE'
                    }
                } else {
                    context.println('openjdk_build_pipeline: Skipping sbom validation because --create-sbom was not found in BUILD_ARGS.')
                }

                // Run Smoke Tests and AQA Tests

                if (currentBuild.currentResult != "SUCCESS") {
                    context.println('[ERROR] Build stages were not successful, not running Smoke tests')
                } else {
                    try {
                        //Only smoke tests succeed TCK and AQA tests will be triggerred.
                        context.println "openjdk_build_pipeline: running smoke tests"
                        if (runSmokeTests() == 'SUCCESS') {
                            context.println "openjdk_build_pipeline: smoke tests OK - running full AQA suite"
                            // Remote trigger Eclipse Temurin JCK tests
                            if (enableTests) {
                                if (buildConfig.VARIANT == 'temurin' && enableTCK) {
                                    remoteTriggerJckTests(filename)
                                }
                                runAQATests(filename)
                            }
                        } else {
                            context.println('[ERROR]Smoke tests are not successful! AQA and TCK tests are blocked ')
                        }
                    } catch (Exception e) {
                        context.println(e.message)
                        currentBuild.result = 'FAILURE'
                    }
                }
                

                // Compare reproducible build if needed
                if (enableReproducibleCompare) {
                    compareReproducibleBuild(nonDockerNodeName)
                }

            // Generic catch all. Will usually be the last message in the log.
            } catch (Exception e) {
                currentBuild.result = 'FAILURE'
                context.println "Execution error: ${e}"

                def sw = new StringWriter()
                def pw = new PrintWriter(sw)
                e.printStackTrace(pw)
                context.println sw.toString()
            }
        }
    }
}

return {
    buildConfigArg,
    USER_REMOTE_CONFIGS,
    DEFAULTS_JSON,
    ADOPT_DEFAULTS_JSON,
    context,
    env,
    currentBuild ->
    def buildConfig
    if (String.isInstance(buildConfigArg)) {
        buildConfig = new IndividualBuildConfig(buildConfigArg as String)
        } else {
        buildConfig = buildConfigArg as IndividualBuildConfig
    }

    return new Build(
            buildConfig,
            USER_REMOTE_CONFIGS,
            DEFAULTS_JSON,
            ADOPT_DEFAULTS_JSON,
            context,
            env,
            currentBuild
        )
}
