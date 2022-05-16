import groovy.json.JsonOutput

gitRefSpec = ""
propagateFailures = false
runTests = enableTests
runParallel = enableTestDynamicParallel
runInstaller = true
runSigner = true
cleanWsBuildOutput = true
jdkVersion = "${JAVA_VERSION}"
isLightweight = true

// if true means this is running in the pr builder pipeline
if (binding.hasVariable('PR_BUILDER')) {
    pipelineSchedule = "0 0 31 2 0" // 31st Feb, so will never run
    gitRefSpec = "+refs/pull/*:refs/remotes/origin/pr/* +refs/heads/master:refs/remotes/origin/master +refs/heads/*:refs/remotes/origin/*"
    propagateFailures = true
    runTests = false
    runParallel = true
    runInstaller = false
    runSigner = false
    isLightweight = false
}

if (!binding.hasVariable('disableJob')) {
    disableJob = false
}

folder("${BUILD_FOLDER}")
folder("${BUILD_FOLDER}/jobs")

pipelineJob("${BUILD_FOLDER}/${JOB_NAME}") {
    description('<h1>THIS IS AN AUTOMATICALLY GENERATED JOB DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1><p>This job is defined in pipeline_job_template.groovy in the ci-jenkins-pipelines repo, if you wish to change it modify that.</p>')
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url("${GIT_URL}")
                        refspec(gitRefSpec)
                        credentials("${CHECKOUT_CREDENTIALS}")
                    }
                    branch("${BRANCH}")
                }
            }
            scriptPath(SCRIPT)
            lightweight(isLightweight)
        }
    }
    disabled(disableJob)

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
        pipelineTriggers {
            triggers {
                cron {
                    spec(pipelineSchedule)
                }
            }
        }
        copyArtifactPermission {
            projectNames('*')
        }
    }

    parameters {
        textParam('targetConfigurations', JsonOutput.prettyPrint(JsonOutput.toJson(targetConfigurations)))
        stringParam('activeNodeTimeout', "0", 'Number of minutes we will wait for a label-matching node to become active.')
        stringParam('jdkVersion', jdkVersion, 'The JDK version of the pipeline e.g (11, 8, 17).')
        stringParam('dockerExcludes', "", 'Map of targetConfigurations to exclude from docker building. If a targetConfiguration (i.e. { "x64LinuxXL": [ "openj9" ], "aarch64Linux": [ "hotspot", "openj9" ] }) has been entered into this field, jenkins will build the jdk without using docker. This param overrides the dockerImage and dockerFile downstream job parameters.')
        stringParam('libraryPath', "", "Relative path to where the import_library.groovy script file is located. It contains the build configurations for each platform, architecture and variant.<br>Default: <code>${defaultsJson['importLibraryScript']}</code>")
        stringParam('baseFilePath', "", "Relative path to where the build_base_file.groovy file is located. This runs the downstream job setup and configuration retrieval services.<br>Default: <code>${defaultsJson['baseFileDirectories']['upstream']}</code>")
        stringParam('buildConfigFilePath', "", "Relative path to where the jdkxx_pipeline_config.groovy file is located. It contains the build configurations for each platform, architecture and variant.<br>Default: <code>${defaultsJson['configDirectories']['build']}/jdkxx_pipeline_config.groovy</code>")
        choiceParam('releaseType', ['Nightly', 'Nightly Without Publish', 'Weekly', 'Release'], 'Nightly - release a standard nightly build.<br/>Nightly Without Publish - run a nightly but do not publish.<br/>Weekly - release a standard weekly build, run with extended tests.<br/>Release - this is a release, this will need to be manually promoted.')
        stringParam('overridePublishName', "", '<strong>REQUIRED for OpenJ9</strong>: Name that determines the publish name (and is used by the meta-data file), defaults to scmReference(minus _adopt if present).<br/>Nightly builds: Leave blank (defaults to a date_time stamp).<br/>OpenJ9 Release build Java 8 example <code>jdk8u192-b12_openj9-0.12.1</code> and for OpenJ9 Java 11 example <code>jdk-11.0.2+9_openj9-0.12.1</code>.')
        stringParam('scmReference', "", 'Tag name or Branch name from which to build. Nightly builds: Defaults to, Hotspot=dev, OpenJ9=openj9, others=master.</br>Release builds: For hotspot JDK8 this would be the OpenJDK tag, for hotspot JDK11+ this would be the Adopt merge tag for the desired OpenJDK tag eg.jdk-11.0.4+10_adopt, and for OpenJ9 this will be the release branch, eg.openj9-0.14.0.')
        stringParam('aqaReference', "", 'Tag name or Branch name of aqa-tests. Defaults to master')
        booleanParam('aqaAutoGen', false, 'If set to true, force auto generate AQA test jobs. Defaults to false')
        booleanParam('enableTests', runTests, 'If set to true the test pipeline will be executed')
        booleanParam('enableTestDynamicParallel', runParallel, 'If set to true test will be run parallel')
        booleanParam('enableInstallers', runInstaller, 'If set to true the installer pipeline will be executed')
        booleanParam('enableSigner', runSigner, 'If set to true the signer pipeline will be executed')
        stringParam('additionalConfigureArgs', "", "Additional arguments that will be ultimately passed to OpenJDK's <code>./configure</code>")
        stringParam('additionalBuildArgs', "", "Additional arguments to be passed to <code>makejdk-any-platform.sh</code>")
        stringParam('overrideFileNameVersion', "", "When forming the filename, ignore the part of the filename derived from the publishName or timestamp and override it.<br/>For instance if you set this to 'FOO' the final file name will be of the form: <code>OpenJDK8U-jre_ppc64le_linux_openj9_FOO.tar.gz</code>")
        booleanParam('useAdoptBashScripts', adoptScripts, "If enabled, the downstream job will pull and execute <code>make-adopt-build-farm.sh</code> from adoptium/temurin-build. If disabled, it will use whatever the job is running inside of at the time, usually it's the default repository in the configuration.")
        booleanParam('cleanWorkspaceBeforeBuild', false, "Clean out the workspace before the build")
        booleanParam('cleanWorkspaceAfterBuild', false, "Clean out the workspace after the build")
        booleanParam('cleanWorkspaceBuildOutputAfterBuild', cleanWsBuildOutput, "Clean out the workspace/build/src/build and workspace/target output only, after the build")
        booleanParam('propagateFailures', propagateFailures, "If true, a failure of <b>ANY</b> downstream build (but <b>NOT</b> test) will cause the whole build to fail")
        booleanParam('keepTestReportDir', false, 'If true, test report dir (including core files where generated) will be kept even when the testcase passes, failed testcases always keep the report dir. Does not apply to JUnit jobs which are always kept, eg.openjdk.')
        booleanParam('keepReleaseLogs', true, 'If true, "Release" type pipeline Jenkins logs will be marked as "Keep this build forever".')
        stringParam('adoptBuildNumber', "", "Empty by default. If you ever need to re-release then bump this number. Currently this is only added to the build metadata file.")
        textParam('defaultsJson', JsonOutput.prettyPrint(JsonOutput.toJson(defaultsJson)), '<strong>DO NOT ALTER THIS PARAM UNLESS YOU KNOW WHAT YOU ARE DOING!</strong> This passes down the user\'s default constants to the downstream jobs.')
        textParam('adoptDefaultsJson', JsonOutput.prettyPrint(JsonOutput.toJson(adoptDefaultsJson)), '<strong>DO NOT ALTER THIS PARAM UNDER ANY CIRCUMSTANCES!</strong> This passes down adopt\'s default constants to the downstream jobs. NOTE: <code>defaultsJson</code> has priority, the constants contained within this param will only be used as a failsafe.')
    }
}
