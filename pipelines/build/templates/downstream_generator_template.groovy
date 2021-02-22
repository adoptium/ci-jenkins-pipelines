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
    description("<h1>THIS IS AN AUTOMATICALLY GENERATED JOB DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1><p>This job is defined in downstream_generator_template.groovy in the ci-jenkins-pipelines repo (seeded by the master_generator.groovy job). If you wish to change it modify either of those files.</p>")
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

    triggers {
        // Execute whenever the master generator finishes
        upstream (
            upstreamProjects: 'master_generator',
            threshold: hudson.model.Result.SUCCESS
        )
    }

    parameters {
        stringParam("REPOSITORY_URL", GIT_URL, "Repository where we will be pulling our parameters from<br>Default: ${DEFAULTS_JSON['repositories']['pipeline_url']}")
        stringParam("REPOSITORY_BRANCH", GIT_BRANCH, "Branch of the REPOSITORY_URL where we will be pulling our parameters from<br>Default: ${DEFAULTS_JSON['repositories']['pipeline_branch']}")
        stringParam("JOB_ROOT", BUILD_FOLDER, "Jenkins folder path where the top level pipeline jobs will be created<br>Default: ${DEFAULTS_JSON['jenkinsDetails']['rootDirectory']}")
        stringParam("JENKINS_BUILD_ROOT", JENKINS_BUILD_ROOT, "Jenkins root url that usually points to where the pipelines are housed (basically it's the full URL of JOB_ROOT).<br>Default: ${DEFAULTS_JSON['jenkinsDetails']['rootUrl']}")
        stringParam("BUILD_FOLDER_PATH", BUILD_FOLDER_PATH, "Directory path that points to where the pipelines build configurations are located.<br>Default: ${DEFAULTS_JSON['configDirectories']['build']}")
        stringParam("NIGHTLY_FOLDER_PATH", NIGHTLY_FOLDER_PATH, "Directory path that points to where the pipelines nightly configurations are located.<br>Warning: If BUILD_CONFIG_PATH is populated, ensure that the targetConfigurations file has a 'u' appended to it if the buildConfigurations file also has one (i.e. jdk8u.groovy) and that the TARGET_CONFIG_PATH file's contents is lain out the same as Adopt's file.<br>Default: ${DEFAULTS_JSON['configDirectories']['nightly']}")
        stringParam("JOB_TEMPLATE_PATH", DOWNSTREAM_JOB_TEMPLATE, "Repository file path to where the downstream job template is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['templateDirectories']['downstream']}")
        stringParam("SCRIPT_PATH", SCRIPT_PATH, "Path to where the top level downstream script is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['scriptDirectories']['downstream']}")
        stringParam("BASE_FILE_PATH", BASE_FILE, "Path to where the downstream basefile script is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['baseFileDirectories']['downstream']}")
        stringParam("REGEN_SCRIPT_PATH", REGEN_FILE, "Path to where the base file generation script is located compared to the repository root. <br>Default: ${DEFAULTS_JSON['baseFileDirectories']['generation']}")
        stringParam("LIBRARY_PATH", LIBRARY_PATH, "Path to where the Adopt class library script is located compared to the repository root.<br>Default: ${DEFAULTS_JSON['importLibraryScript']}")
        stringParam("SLEEP_TIME", SLEEP_TIME, "Time (in seconds) for the job to sleep if it detects a downstream job is running.<br>Default: Default: 900 (15mins)")
        textParam("EXCLUDES_LIST", EXCLUDES_LIST, "Map of targetConfigurations to exclude from generation. In essence, if a targetConfiguration (i.e. { 'x64LinuxXL': [ 'openj9' ], 'aarch64Linux': [ 'hotspot', 'openj9' ] }) has been entered into this field, jenkins will exclude it from generation.")
        credentialsParam("CHECKOUT_CREDENTIALS") {
            defaultValue(binding.getVariable("checkoutCreds")),
            type("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"),
            description("GitHub username and password/PAC used to authenticate the client when checking out a custom repository that isn't Adopt's and/or is private. These are passed down to the upstream pipeline jobs.\n\nYou may get an Error code of 401 if the credentials are invalid, a code of 403 if they are not provided at all or a message (`Warning: CredentialId foobar could not be found.`) if you have used a user credential. See https://issues.jenkins.io/browse/JENKINS-60349?attachmentOrder=desc")
        }
        credentialsParam("JENKINS_AUTH") {
            defaultValue(binding.getVariable("jenkinsCreds")),
            type("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl"),
            description("Jenkins Username/Password combination used to authenticate the client with the Jenkins api. NOTE: The username will likely be your own Jenkins username but the password will be your Jenkins api token.<br><br>To generate one, click your name on the top right corner then click 'Configure' to see your API token (see https://www.jenkins.io/doc/book/system-administration/authenticating-scripted-clients/ for more info).<br><br>Providing invalid credentials will result in an error message similar to the following:<br>Error: java.io.IOException: Server returned HTTP response code: 401<br><br>Failing to provide this value may result in an error message similar to the following:<br>Error: java.io.IOException: Server returned HTTP response code: 403")
        }
        textParam("DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(DEFAULTS_JSON)), "<strong>DO NOT ALTER THIS PARAM UNLESS YOU KNOW WHAT YOU ARE DOING!</strong> This passes down the user's default constants to the downstream jobs.")
        textParam("ADOPT_DEFAULTS_JSON", JsonOutput.prettyPrint(JsonOutput.toJson(ADOPT_DEFAULTS_JSON)), "<strong>DO NOT ALTER THIS PARAM UNDER ANY CIRCUMSTANCES!</strong> This passes down adopt's default constants to the downstream jobs. NOTE: <code>defaultsJson</code> has priority, the constants contained within this param will only be used as a failsafe.")
        stringParam("JAVA_VERSION", "jdk${JAVA_VERSION}", "<strong>You shouldn't need to alter this param. All it does is inform the common script what version we are building (passed down by the seed job).</strong>")
    }
}
