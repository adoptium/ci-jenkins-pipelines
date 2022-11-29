import groovy.json.JsonOutput

String gitRefSpec = ''
Boolean propagateFailures = false
Boolean runTests = true
Boolean runParallel = true
Boolean runInstaller = true
Boolean runSigner = true
Boolean cleanWsBuildOutput = true
Boolean isLightweight = false  // since this is to checkout on a releaseTag, better to use false or might get not found tag/SHA1

folder("${BUILD_FOLDER}")

pipelineJob("${BUILD_FOLDER}/${JOB_NAME}") {
    description('<h1>THIS IS AN AUTOMATICALLY GENERATED JOB DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1><p>This job is defined in release_pipeline_job_template.groovy in the ci-jenkins-pipelines repo, if you wish to change it modify that.</p>')
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url("${GIT_URL}")
                        refspec("${gitRefSpec}")
                    }
                    branch("${releaseTag}")
                }
            }
            scriptPath("${SCRIPT}")
            lightweight(isLightweight)
        }
    }
    disabled(false)

    logRotator {
        numToKeep(60)
        artifactNumToKeep(2)
    }

    properties {
        // Hide top level pipeline access from the public as they contain non Temurin artefacts
        authorizationMatrix {
            inheritanceStrategy {
                // Do not inherit permissions from global configuration
                nonInheriting()
            }
            permissions(['hudson.model.Item.Build:AdoptOpenJDK*build', 'hudson.model.Item.Build:AdoptOpenJDK*build-triage',
            'hudson.model.Item.Cancel:AdoptOpenJDK*build', 'hudson.model.Item.Cancel:AdoptOpenJDK*build-triage',
            'hudson.model.Item.Configure:AdoptOpenJDK*build', 'hudson.model.Item.Configure:AdoptOpenJDK*build-triage',
            'hudson.model.Item.Read:AdoptOpenJDK*build', 'hudson.model.Item.Read:AdoptOpenJDK*build-triage',
            // eclipse-temurin-bot needs read access for TRSS
            'hudson.model.Item.Read:eclipse-temurin-bot',
            // eclipse-temurin-compliance bot needs read access for https://ci.eclipse.org/temurin-compliance
            'hudson.model.Item.Read:eclipse-temurin-compliance-bot',
            'hudson.model.Item.Workspace:AdoptOpenJDK*build', 'hudson.model.Item.Workspace:AdoptOpenJDK*build-triage',
            'hudson.model.Run.Update:AdoptOpenJDK*build', 'hudson.model.Run.Update:AdoptOpenJDK*build-triage'])
        }
        copyArtifactPermission {
            projectNames('*')
        }
    }

    parameters {
        // important items to verify before trigger release pipeline
        textParam('targetConfigurations', JsonOutput.prettyPrint(JsonOutput.toJson(targetConfigurations)))
        stringParam('releaseType', 'Release', "only for release purpose")
        booleanParam('useAdoptBashScripts', false, "If enabled, the downstream job will pull and execute <code>make-adopt-build-farm.sh</code> from adoptium/temurin-build. If disabled, it will use whatever the job is running inside of at the time, usually it's the default repository in the configuration.")
        stringParam('scmReference', '', 'Tag name or Branch name from which openjdk source code repo to build')
        stringParam('buildReference', releaseTag, 'SHA1 or Tag name or Branch name of temurin-build repo. Defaults to master')
        stringParam('ciReference', releaseTag, 'SHA1 or Tag name or Branch name of ci-jenkins-pipeline repo. Defaults to master')
        stringParam('helperReference', releaseTag, 'Tag name or Branch name of jenkins-helper repo. Defaults to master')
        stringParam('aqaReference', '', 'Tag name or Branch name of aqa-tests. Defaults to master')
        // special items need to modify per jdk8
        stringParam('overridePublishName', '', 'Most useful for jdk8 arm32 with a different scmReference tag')
        stringParam('additionalConfigureArgs', "--without-version-pre --without-version-opt", "Additional arguments that will be ultimately passed to OpenJDK's <code>./configure</code>")

        // default value not matter for release
        stringParam('jdkVersion', "${JAVA_VERSION}")
        stringParam('activeNodeTimeout', '0', 'Number of minutes we will wait for a label-matching node to become active.')
        stringParam('dockerExcludes', '', 'Map of targetConfigurations to exclude from docker building. If a targetConfiguration (i.e. { "x64LinuxXL": [ "openj9" ], "aarch64Linux": [ "hotspot", "openj9" ] }) has been entered into this field, jenkins will build the jdk without using docker. This param overrides the dockerImage and dockerFile downstream job parameters.')
        stringParam('baseFilePath', '', "Relative path to where the build_base_file.groovy file is located. This runs the downstream job setup and configuration retrieval services.<br>Default: <code>${defaultsJson['baseFileDirectories']['upstream']}</code>")
        stringParam('buildConfigFilePath', '', "Relative path to where the jdkxx_pipeline_config.groovy file is located. It contains the build configurations for each platform, architecture and variant.<br>Default: <code>${defaultsJson['configDirectories']['build']}/jdkxx_pipeline_config.groovy</code>")
        booleanParam('aqaAutoGen', false, 'If set to true, force auto generate AQA test jobs. Defaults to false')
        booleanParam('enableTests', runTests, 'If set to true the test pipeline will be executed')
        booleanParam('enableTestDynamicParallel', runParallel, 'If set to true test will be run parallel')
        booleanParam('enableInstallers', runInstaller, 'If set to true the installer pipeline will be executed')
        booleanParam('enableSigner', runSigner, 'If set to true the signer pipeline will be executed')
        stringParam('additionalBuildArgs', '', 'Additional arguments to be passed to <code>makejdk-any-platform.sh</code>')
        stringParam('overrideFileNameVersion', '', "When forming the filename, ignore the part of the filename derived from the publishName or timestamp and override it.<br/>For instance if you set this to 'FOO' the final file name will be of the form: <code>OpenJDK8U-jre_ppc64le_linux_openj9_FOO.tar.gz</code>")
        booleanParam('cleanWorkspaceBeforeBuild', false, 'Clean out the workspace before the build')
        booleanParam('cleanWorkspaceAfterBuild', false, 'Clean out the workspace after the build')
        booleanParam('cleanWorkspaceBuildOutputAfterBuild', cleanWsBuildOutput, 'Clean out the workspace/build/src/build and workspace/target output only, after the build')
        booleanParam('propagateFailures', propagateFailures, 'If true, a failure of <b>ANY</b> downstream build (but <b>NOT</b> test) will cause the whole build to fail')
        booleanParam('keepTestReportDir', false, 'If true, test report dir (including core files where generated) will be kept even when the testcase passes, failed testcases always keep the report dir. Does not apply to JUnit jobs which are always kept, eg.openjdk.')
        booleanParam('keepReleaseLogs', true, 'If true, "Release" type pipeline Jenkins logs will be marked as "Keep this build forever".')
        stringParam('adoptBuildNumber', '', 'Empty by default. If you ever need to re-release then bump this number. Currently this is only added to the build metadata file.')
        textParam('defaultsJson', JsonOutput.prettyPrint(JsonOutput.toJson(defaultsJson)), '<strong>DO NOT ALTER THIS PARAM UNLESS YOU KNOW WHAT YOU ARE DOING!</strong> This passes down the user\'s default constants to the downstream jobs.')
        textParam('adoptDefaultsJson', JsonOutput.prettyPrint(JsonOutput.toJson(adoptDefaultsJson)), '<strong>DO NOT ALTER THIS PARAM UNDER ANY CIRCUMSTANCES!</strong> This passes down adoptium\'s default constants to the downstream jobs. NOTE: <code>defaultsJson</code> has priority, the constants contained within this param will only be used as a failsafe.')
    }
}
