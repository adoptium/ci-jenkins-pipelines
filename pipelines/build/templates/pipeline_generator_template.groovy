/* groovylint-disable */
// Disable groovy lint as it thinks it's a map yet this is actually how the jobDsl plugin is supposed to look

import groovy.json.JsonOutput
import hudson.model.Result

// if true means this is running in the pr builder pipeline
if (binding.hasVariable("PR_BUILDER")) {
    gitRefSpec = "+refs/pull/*:refs/remotes/origin/pr/* +refs/heads/master:refs/remotes/origin/master +refs/heads/*:refs/remotes/origin/*"
}

folder("${GENERATION_FOLDER}")

pipelineJob("${GENERATION_FOLDER}/${JOB_NAME}") {
    description("<h1>THIS IS AN AUTOMATICALLY GENERATED JOB DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1><p>This job is defined in pipeline_generator_template.groovy in the ci-jenkins-pipelines repo (seeded by the master_generator.groovy job). If you wish to change it modify either of those files.</p>")
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url("${GIT_URL}")
                        refspec(gitRefSpec)
                        credentials("${CHECKOUT_CREDENTIALS}")
                    }
                    branch("${GIT_BRANCH}")
                }
            }
            scriptPath("${SCRIPT}")
        }
    }

    properties {
        githubProjectUrl("${GIT_URL}")
    }

    logRotator {
        numToKeep(60)
    }

    // Execute whenever the master generator finishes
    triggers {
        upstream (
            upstreamProjects: 'master_generator',
            threshold: hudson.model.Result.SUCCESS
        )
    }

    parameters {
        stringParam("REPOSITORY_URL", GIT_URL, "Repository where we will be pulling our parameters from<br>Default: ${DEFAULTS_JSON['repositories']['pipeline_url']}")
        stringParam("REPOSITORY_BRANCH", GIT_BRANCH, "Branch of the REPOSITORY_URL where we will be pulling our parameters from<br>Default: ${DEFAULTS_JSON['repositories']['pipeline_branch']}")
        stringParam("SCRIPT_FOLDER_PATH", SCRIPT_FOLDER_PATH, "Repository folder path to where the top level pipeline code (e.g. openjdk_pipeline.groovy) is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['scriptDirectories']['upstream']}")
        stringParam("BASE_FILE_PATH", BASE_FILE, "Path to where the downstream basefile script is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['baseFileDirectories']['upstream']}")
        stringParam("NIGHTLY_FOLDER_PATH", NIGHTLY_FOLDER_PATH, "Repository folder path to where the default nightly builds are declared (e.g. jdkxx.groovy) compared to the repository root.<br>Default: ${DEFAULTS_JSON['configDirectories']['nightly']}")
        stringParam("BUILD_FOLDER_PATH", BUILD_FOLDER_PATH, "Directory path that points to where the pipelines build configurations are located.<br>Default: ${DEFAULTS_JSON['configDirectories']['build']}")
        stringParam("JOB_TEMPLATE_PATH", UPSTREAM_JOB_TEMPLATE, "Repository file path to where the pipeline job template is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['templateDirectories']['upstream']}")
        stringParam("WEEKLY_TEMPLATE_PATH", WEEKLY_JOB_TEMPLATE, "Repository file path to where the weekly pipeline job template is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['templateDirectories']['weekly']}")
        stringParam("LIBRARY_PATH", LIBRARY_PATH, "Path to where the Adopt class library script is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['importLibraryScript']}")
        stringParam("JOB_ROOT", BUILD_FOLDER, "Jenkins folder path where the top level pipeline jobs will be created<br>Default: ${DEFAULTS_JSON['jenkinsDetails']['rootDirectory']}")
        booleanParam("ENABLE_PIPELINE_SCHEDULE", ENABLE_PIPELINE_SCHEDULE, "The top level pipeline jobs can be generated with a triggerSchedule pulled from the config files. If set to false, this parameter overrides this logic and generates pipeline jobs without a trigger (i.e. the jobs won't be executed automatically if this is NOT ticked)")
        booleanParam("USE_ADOPT_SHELL_SCRIPTS", USE_ADOPT_SHELL_SCRIPTS, "This determines whether we will checkout to adopt's repository before running make-adopt-build-farm.sh or if we use the user's bash scripts.")
        credentialsParam("CHECKOUT_CREDENTIALS") {
            defaultValue(binding.getVariable("checkoutCreds")),
            type("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"),
            description("GitHub username and password/PAC used to authenticate the client when checking out a custom repository that isn't Adopt's and/or is private. These are passed down to the upstream pipeline jobs.\n\nYou may get an Error code of 401 if the credentials are invalid, a code of 403 if they are not provided at all or a message (`Warning: CredentialId foobar could not be found.`) if you have used a user credential. See https://issues.jenkins.io/browse/JENKINS-60349?attachmentOrder=desc")
        }
        textParam("DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(DEFAULTS_JSON)), "<strong>DO NOT ALTER THIS PARAM UNLESS YOU KNOW WHAT YOU ARE DOING!</strong> This passes down the user's default constants to the downstream jobs.")
        textParam("ADOPT_DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(ADOPT_DEFAULTS_JSON)), "<strong>DO NOT ALTER THIS PARAM UNDER ANY CIRCUMSTANCES!</strong> This passes down adopt's default constants to the downstream jobs. NOTE: <code>defaultsJson</code> has priority, the constants contained within this param will only be used as a failsafe.")
        stringParam("RETIRED_VERSIONS", RETIRED_VERSIONS, "<strong>You shouldn't need to alter this param. All it does is inform the common script what versions are not built at adopt anymore (passed down by the seed job).</strong>")
    }
}