import groovy.json.JsonOutput

folder("${BUILD_FOLDER}")

def pipelineReleaseType = "${releaseType}"

pipelineJob("${BUILD_FOLDER}/${JOB_NAME}") {
    description('<h1>THIS IS AN AUTOMATICALLY GENERATED JOB DO NOT MODIFY, IT WILL BE OVERWRITTEN.</h1><p>This job is defined in weekly_release_pipeline_job_template.groovy in the ci-jenkins-pipelines repo, if you wish to change it modify that.</p>')
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url("${GIT_URL}")
                        credentials("${CHECKOUT_CREDENTIALS}")
                    }
                    branch("${BRANCH}")
                }
            }
            scriptPath(SCRIPT)
            lightweight(true)
        }
    }
    disabled(disableJob)

    logRotator {
        numToKeep(60)
        artifactNumToKeep(2)
    }

    properties {
        pipelineTriggers {
            triggers {
                cron {
                    spec(pipelineSchedule)
                }
            }
        }
    }

    parameters {
        stringParam('buildPipeline', "${BUILD_FOLDER}/${PIPELINE}", 'The build pipeline to invoke.')
        choiceParam('releaseType', [pipelineReleaseType, 'Nightly Without Publish', 'Nightly', 'Weekly', 'Weekly Without Publish', 'Release'].unique(), 'Nightly - release a standard nightly build.<br/>Nightly Without Publish - run a nightly but do not publish.<br/>Weekly - release a standard weekly build, run with extended tests.<br/>Weekly Without Publish - run a weekly but do not publish.<br/>Release - this is a release, this will need to be manually promoted.')
        textParam('scmReferences', JsonOutput.prettyPrint(JsonOutput.toJson(weekly_release_scmReferences)), 'The map of scmReferences for each variant.')
        textParam('targetConfigurations', JsonOutput.prettyPrint(JsonOutput.toJson(targetConfigurations)), 'The map of platforms and variants to build.')
    }
}
