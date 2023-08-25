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

import groovy.json.JsonSlurper
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit


// Get the latest upstream openjdk build tag
def getLatestOpenjdkBuildTag(String version) {
    def openjdkRepo = "https://github.com/openjdk/${version}.git"

    def latestTag = sh(returnStdout: true, script:"git ls-remote --sort=-v:refname --tags ${openjdkRepo} | grep -v \"\\^{}\" | tr -s \"\\t \" \" \" | cut -d\" \" -f2 | sed \"s,refs/tags/,,\" | sort -V -r | head -1")
    echo "latest ${version} tag = ${latestTag}"

    return latestTag
}

// Get the latest release tag from the binaries repo
def getLatestBinariesTag(String version) {
    def binariesRepo = "https://github.com/${params.BINARIES_REPO}".replaceAll("_NN_", version)

    def latestTag = sh(returnStdout: true, script:"git ls-remote --sort=-v:refname --tags ${binariesRepo} | grep \"\\-ea\\-beta\" | grep -v \"\\^{}\" | tr -s \"\\t \" \" \" | cut -d\" \" -f2 | sed \"s,refs/tags/,,\" | sort -V -r | head -1")
    echo "latest jdk${version} tag = ${latestTag}"

    return latestTag    
}

// Verify the given release contains all the expected assets
def verifyReleaseContent(String version, String release, Map status) {
    echo "Verifying ${version} asserts in release: ${release}"
    status['assets'] = "Good"

    def releaseAssetsUrl = "https://api.github.com/repos/${params.BINARIES_REPO}/releases/tags/${release}".replaceAll("_NN_", version.replaceAll("u","").replaceAll("jdk",""))

    def releaseAssets = sh(script: "curl -L '${releaseAssetsUrl}' | grep '\"name\"'", returnStdout: true)
    if (releaseAssets == "") {
        echo "Error loading release assets list for ${releaseAssetsUrl}"
        status['assets'] = "Error loading ${releaseAssetsUrl}"
    } else {
        def configFile = "${version}.groovy"   
        def targetConfigPath = "${params.BUILD_CONFIG_URL}/${configFile}"
        echo "    Loading pipeline config file: ${targetConfigPath}"
        def rc = sh(script: "curl -LO ${targetConfigPath}", returnStatus: true)
        if (rc != 0) {
            echo "Error loading ${targetConfigPath}"
            status['assets'] = "Error loading ${targetConfigPath}"
        } else {
            // Load the targetConfiguration
            targetConfigurations = null
            load configFile

            def archToAsset = [x64Linux:   "x64_linux",
                               x64Windows: "x64_windows",
                               x64Mac:     "x64_mac",
                               x64AlpineLinux: "x64_alpine-linux",
                               ppc64Aix:   "ppc64_aix",
                               ppc64leLinux: "ppc64le_linux",
                               s390xLinux: "s390x_linux",
                               aarch64Linux: "aarch64_linux",
                               aarch64Mac: "aarch64_mac",
                               arm32Linux: "arm_linux",
                               x32Windows: "x86-32_windows",
                               x64Solaris: "x64_solaris",
                               sparcv9Solaris: "sparcv9_solaris"
                              ]
                               
            def missingAssets = []
            targetConfigurations.keySet().each { osarch ->
                echo "    Verifying : $osarch"

                def imagetypes = ["debugimage", "jdk", "jre", "sbom"]
                if (version != "jdk8u") {
                    imagetypes.add("static-libs")
                    imagetypes.add("testimage")
                }

                def filetypes
                def jdkjre_filetypes
                if (osarch.contains("Windows")) {
                    filetypes =        ["\\.zip", "\\.zip\\.json", "\\.zip\\.sha256\\.txt", "\\.zip\\.sig"]
                    jdkjre_filetypes = ["\\.msi", "\\.msi\\.json", "\\.msi\\.sha256\\.txt", "\\.msi\\.sig"]
                    jdkjre_filetypes.add(filetypes)
                } else if (osarch.contains("Mac")) {
                    filetypes        = ["\\.tar\\.gz", "\\.tar\\.gz\\.json", "\\.tar\\.gz\\.sha256\\.txt", "\\.tar\\.gz\\.sig"]
                    jdkjre_filetypes = ["\\.pkg", "\\.pkg\\.json", "\\.pkg\\.sha256\\.txt", "\\.pkg\\.sig"]
                    jdkjre_filetypes.add(filetypes)
                } else {
                    filetypes = ["\\.tar\\.gz", "\\.tar\\.gz\\.json", "\\.tar\\.gz\\.sha256\\.txt", "\\.tar\\.gz\\.sig"]
                    jdkjre_filetypes = filetypes
                }
                def sbom_filetypes = ["\\.json", "\\.json\\.sig", "-metadata\\.json"]

                imagetypes.each { image ->
                    def ftypes
                    if (image == "jdk" || image == "jre") {
                        ftypes = jdkjre_filetypes
                    } else if (image == "sbom") {
                        ftypes = sbom_filetypes
                    } else {
                        ftypes = filetypes
                    }
                    ftypes.each { ftype ->
                        def arch_fname = archToAsset[osarch]
                        def findAsset = releaseAssets =~/"(?s).*${image}_${arch_fname}_[^\"]*${ftype}\".*"/
                        if (!findAsset) {
                            def missing="$osarch : $image : $ftype".replaceAll("\\\\", "")
                            echo "    Missing asset: ${missing}"
                            missingAssets.add(missing)
                            status['assets'] = "Missing assets"
                        }
                    }
                }
            }
        }
    }
}

node('worker') {
    def variant = "${params.VARIANT}"
    def trssUrl    = "${params.TRSS_URL}"
    def apiUrl    = "${params.API_URL}"
    def slackChannel = "${params.SLACK_CHANNEL}"
    def featureReleases = "${params.FEATURE_RELEASES}".split("[, ]+")   //[ "jdk8u", "jdk11u", "jdk17u", "jdk21" ] // Consider making those parameters
    def tipRelease      = "${params.TIP_RELEASE}"  //"jdk22" // Current jdk(head) version
    def nightlyStaleDays = "${params.MAX_NIGHTLY_STALE_DAYS}"
    def amberBuildAlertLevel = params.AMBER_BUILD_ALERT_LEVEL ? params.AMBER_BUILD_ALERT_LEVEL as Integer : -99
    def amberTestAlertLevel  = params.AMBER_TEST_ALERT_LEVEL  ? params.AMBER_TEST_ALERT_LEVEL as Integer : -99

    def healthStatus = []
    def testStats = []

    stage('getPipelineStatus') {
        def apiVariant = variant
        if (apiVariant == 'temurin') {
            apiVariant = 'hotspot'
        }
        if (apiVariant == 'hotspot') { // hotspot only for now
            // Determine nightly pipeline health by looking at published assets.
            // In particular, look at first data set for latest published binaries.
            // If no published assets happened the last 4 days, the nightly pipeline
            // is considered unhealthy.
            // For tag triggered versions (jdk-21+) check the binary is published
            // The release asset list is also verified
            featureReleases.each { featureRelease ->
              def featureReleaseInt = featureRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
              def assets = sh(returnStdout: true, script: "wget -q -O - '${apiUrl}/v3/assets/feature_releases/${featureReleaseInt}/ea?image_type=jdk&sort_method=DATE&pages=1&jvm_impl=${apiVariant}'")
              def assetsJson = new JsonSlurper().parseText(assets)
              def releaseName = assetsJson[0].release_name
              def status = []
              if (featureReleaseInt < 21) {
                def ts = assetsJson[0].timestamp // newest timestamp of a jdk asset
                def assetTs = Instant.parse(ts).atZone(ZoneId.of('UTC'))
                def now = ZonedDateTime.now(ZoneId.of('UTC'))
                def days = ChronoUnit.DAYS.between(assetTs, now)
                status = [releaseName: releaseName, maxStaleDays: nightlyStaleDays, actualDays: days]
              } else {
                def latestOpenjdkBuild = getLatestOpenjdkBuildTag(featureRelease)
                status = [releaseName: releaseName, expectedReleaseName: "${latestOpenjdkBuild}-ea-beta"]
              }

              // Verify the given release contains all the expected assets
              verifyReleaseContent(featureRelease, releaseName, status)
              healthStatus[featureReleaseInt] = status
            }

            // Check tip_release status, by querying binaries repo as API does not server the "tip" dev release
            def latestOpenjdkBuild = getLatestOpenjdkBuildTag("jdk")
            def tipVersion = tipRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
            def releaseName = getLatestBinariesTag("${tipVersion}")
            status = [releaseName: releaseName, expectedReleaseName: "${latestOpenjdkBuild}-ea-beta"]
            verifyReleaseContent(tipRelease, releaseName, status)
            healthStatus[tipVersion] = status
           
        }
    }

    // Get the last Nightly build and test job & case stats
    stage('getStats') {
        // Determine build and test variant job name search strings
        def buildVariant = variant
        def testVariant
        if (variant == 'temurin' || variant == 'hotspot') { //variant == "hotspot" should be enough for now. Keep temurin for later.
            testVariant = '_hs_'
        } else if (variant == 'openj9') {
            testVariant = '_j9_'
        } else {
            testVariant = "_${variant}_"
        }

        // Get top level builds names
        def trssBuildNames = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getTopLevelBuildNames?type=Test")
        def buildNamesJson = new JsonSlurper().parseText(trssBuildNames)
        buildNamesJson.each { build ->
            // Is it a build Pipeline? Excluding "evaluation-" pipelines
            if (build._id.buildName.contains('-pipeline') && !build._id.buildName.startsWith('evaluation-')) {
                echo "Pipeline ${build._id.buildName}"
                def pipelineName = build._id.buildName

                // Find the last "Done" pipeline builds started by "timer", "weekly-" or "releaseTrigger"
                def pipeline = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getBuildHistory?buildName=${pipelineName}")
                def pipelineJson = new JsonSlurper().parseText(pipeline)
                def foundBuild = false
                if (pipelineJson.size() > 0) {
                    // Find first in list started by "timer", "weekly-" or "releaseTrigger"
                    pipelineJson.each { job ->
                        if (!foundBuild) {
                            def pipeline_id = null
                            def pipelineUrl
                            def buildJobComplete = 0
                            def buildJobFailure = 0
                            def testJobSuccess = 0
                            def testJobUnstable = 0
                            def testJobFailure = 0
                            def testCasePassed = 0
                            def testCaseFailed = 0
                            def testCaseDisabled = 0
                            def testJobNumber = 0
                            def buildJobNumber = 0

                            // Determine when job ran?
                            def build_time = LocalDateTime.ofInstant(Instant.ofEpochMilli(job.timestamp), ZoneId.of('UTC'))
                            def now = LocalDateTime.now(ZoneId.of('UTC'))
                            def days = ChronoUnit.DAYS.between(build_time, now)

                            // Was job "Done"?
                            // Report release- pipelines only if built within the last week
                            if (job.status != null && job.status.equals('Done') && job.startBy != null &&
                                (!build._id.buildName.startsWith('release-') || days < 7)) {
                                if (job.startBy.startsWith('timer')) {
                                    // Nightly scheduled job
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
                                    foundBuild = true
                                } else if (job.startBy.startsWith("upstream project \"build-scripts/weekly-")) {
                                    // Weekend weekly scheduled job
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
                                    foundBuild = true
                                } else if (job.startBy.startsWith("upstream project \"build-scripts/utils/releaseTrigger_")) {
                                    // Build tag triggered build
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
                                    foundBuild = true
                                }
                            }
                            // Was job a "match"?
                            if (pipeline_id != null) {
                                // Get all child Test jobs for this pipeline job
                                def pipelineTestJobs = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getAllChildBuilds?parentId=${pipeline_id}\\&buildNameRegex=^Test_.*${testVariant}.*")
                                def pipelineTestJobsJson = new JsonSlurper().parseText(pipelineTestJobs)
                                if (pipelineTestJobsJson.size() > 0) {
                                    testJobNumber = pipelineTestJobsJson.size()
                                    pipelineTestJobsJson.each { testJob ->
                                        if (testJob.buildResult.equals('SUCCESS')) {
                                            testJobSuccess += 1
                                        } else if (testJob.buildResult.equals('UNSTABLE')) {
                                            testJobUnstable += 1
                                        } else {
                                            testJobFailure += 1
                                        }
                                        if (testJob.testSummary != null) {
                                            testCasePassed += testJob.testSummary.passed
                                            testCaseFailed += testJob.testSummary.failed
                                            testCaseDisabled += testJob.testSummary.disabled
                                        }
                                    }
                                }
                                // Get all child Build jobs for this pipeline job
                                def pipelineBuildJobs = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getChildBuilds?parentId=${pipeline_id}")
                                def pipelineBuildJobsJson = new JsonSlurper().parseText(pipelineBuildJobs)
                                buildJobNumber = 0
                                if (pipelineBuildJobsJson.size() > 0) {
                                    pipelineBuildJobsJson.each { buildJob ->
                                        if (buildJob.buildName.contains(buildVariant)) {
                                            buildJobNumber += 1
                                            if (buildJob.buildResult.equals('FAILURE')) {
                                                buildJobFailure += 1
                                            } else {
                                                buildJobComplete += 1
                                            }
                                        }
                                    }
                                }

                                def testResult = [name: pipelineName, url: pipelineUrl,
                                      buildJobNumber:   buildJobNumber,
                                      buildJobComplete:  buildJobComplete,
                                      buildJobFailure:  buildJobFailure,
                                      testJobSuccess:   testJobSuccess,
                                      testJobUnstable:  testJobUnstable,
                                      testJobFailure:   testJobFailure,
                                      testCasePassed:   testCasePassed,
                                      testCaseFailed:   testCaseFailed,
                                      testCaseDisabled: testCaseDisabled,
                                      testJobNumber:    testJobNumber]
                                testStats.add(testResult)
                            }
                        }
                    }
                }
            }
        }
    }

    // Print the results of the nightly build/test stats
    stage('printBuildTestStats') {
        def buildFailures = 0
        def nightlyTestSuccessRating = 0
        def numTestPipelines = 0
        def totalBuildJobs = 0
        def totalTestJobs = 0
        testStats.each { pipeline ->
            echo "For Variant: ${variant}"
            echo "  Pipeline : ${pipeline.name} : ${pipeline.url}"
            echo "    => Number of Build jobs = ${pipeline.buildJobNumber}"
            echo "    => Build job COMPLETE   = ${pipeline.buildJobComplete}"
            echo "    => Build job FAILURE   = ${pipeline.buildJobFailure}"
            echo "    => Number of Test jobs = ${pipeline.testJobNumber}"
            echo "    => Test job SUCCESS    = ${pipeline.testJobSuccess}"
            echo "    => Test job UNSTABLE   = ${pipeline.testJobUnstable}"
            echo "    => Test job FAILURE    = ${pipeline.testJobFailure}"
            echo "    => Test case Passed    = ${pipeline.testCasePassed}"
            echo "    => Test case Failed    = ${pipeline.testCaseFailed}"
            echo "    => Test case Disabled  = ${pipeline.testCaseDisabled}"
            echo '==================================================================================='
            totalBuildJobs += pipeline.buildJobNumber
            buildFailures += pipeline.buildJobFailure
            totalTestJobs += pipeline.testJobNumber
            // Did test jobs run? (build may have failed)
            if (pipeline.testJobNumber > 0) {
                numTestPipelines += 1
                // Pipeline Test % success rating: %(SucceededOrUnstable) - %(FailedTestCases)
                nightlyTestSuccessRating += (((pipeline.testJobNumber - pipeline.testJobFailure) * 100 / pipeline.testJobNumber))
                // Did test cases run?
                if ((pipeline.testCasePassed + pipeline.testCaseFailed) > 0) {
                    nightlyTestSuccessRating -= (pipeline.testCaseFailed * 100 / (pipeline.testCasePassed + pipeline.testCaseFailed))
                }
            }
        }
        // Average test success rating across all pipelines
        if (numTestPipelines > 0) {
            nightlyTestSuccessRating = nightlyTestSuccessRating / numTestPipelines
    } else {
            // If no Tests were run assume 0% success
            nightlyTestSuccessRating = 0
        }

        // Build % success rating: Successes as % of build total
        def buildSuccesses = totalBuildJobs - buildFailures
        def nightlyBuildSuccessRating = 0
        if (totalBuildJobs > 0) {
            nightlyBuildSuccessRating = ((buildSuccesses) * 100) / (totalBuildJobs)
    } else {
            // If no Builds were run assume 0% success
            nightlyBuildSuccessRating = 0
        }

        // Overall % success rating: Average build & test % success rating
        def overallNightlySuccessRating = ((nightlyBuildSuccessRating + nightlyTestSuccessRating) / 2).intValue()

        echo "======> Success Rating for variant: ${variant}"
        echo "======> Total number of Build jobs    = ${totalBuildJobs}"
        echo "======> Total number of Test jobs     = ${totalTestJobs}"
        echo "======> Nightly Build Success Rating  = ${nightlyBuildSuccessRating.intValue()} %"
        echo "======> Nightly Test Success Rating   = ${nightlyTestSuccessRating.intValue()} %"
        echo "======> Overall Nightly Build & Test Success Rating = ${overallNightlySuccessRating} %"

        def statusColor = 'good'
        if (nightlyBuildSuccessRating.intValue() < amberBuildAlertLevel || nightlyTestSuccessRating.intValue() < amberTestAlertLevel) {
            statusColor = 'warning'
        }

        // Slack message:
        //slackSend(channel: slackChannel, color: statusColor, message: 'Adoptium Nightly Build Success : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>')

echo 'Adoptium Nightly Build Success : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>'
    }

    stage('printPublishStats') {
        if (variant == 'temurin' || variant == 'hotspot') { //variant == "hotspot" should be enough for now. Keep temurin for later.
            echo '-------------- Nightly pipeline health report ------------------'
            featureReleases.each { featureRelease ->
                def featureReleaseInt = featureRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
                def status = healthStatus[featureReleaseInt]
                def days = status['actualDays'] as int
                def msg = "${days} day(s) ago" // might actually be days + N hours, where N < 24
                if (status['actualDays'] == 0) {
                    msg = 'less than 24 hours ago'
                }
                def maxDays = status['maxStaleDays'] as int
                def fullMessage = "JDK ${featureRelease} nightly pipeline publish status: healthy. Last published: ${msg}"
                def slackColor = 'good'
                if (maxDays <= days) {
                    slackColor = 'warning'
                    fullMessage = "JDK ${featureRelease} nightly pipeline publish status: unhealthy. Last published: ${msg}. Stale threshold: ${maxDays} days."
                }
                echo "===> ${fullMessage}"
                // One slack message per JDK version:
                //slackSend(channel: slackChannel, color: slackColor, message: fullMessage)
            }
            echo '----------------------------------------------------------------'
        }
    }
}

