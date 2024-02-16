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

    // Need to include jdk8u to avoid picking up old tag format    
    def jdk8Filter = (version == "jdk8u") ? "| grep 'jdk8u'" : ""

    def latestTag = sh(returnStdout: true, script:"git ls-remote --sort=-v:refname --tags ${openjdkRepo} | grep -v '\\^{}' | tr -s '\\t ' ' ' | cut -d' ' -f2 | sed \"s,refs/tags/,,\" | grep -v '\\-ga' ${jdk8Filter} | sort -V -r | head -1 | tr -d '\\n'")
    echo "latest upstream openjdk/${version} tag = ${latestTag}"

    return latestTag
}

// Get how long ago the given upstream tag was published?
def getOpenjdkBuildTagAge(String version, String tag) {
    def openjdkRepo = "https://github.com/openjdk/${version}.git"

    def date = sh(returnStdout: true, script:"(rm -rf tmpRepo; git clone --depth 1 --branch ${tag} ${openjdkRepo} tmpRepo; cd tmpRepo; git log --tags --simplify-by-decoration --pretty=\"format:PUBLISH_DATE=%cI\") | grep PUBLISH_DATE | cut -d\"=\" -f2 | tr -d '\\n'")
    def tagTs = Instant.parse(date).atZone(ZoneId.of('UTC'))
    def now = ZonedDateTime.now(ZoneId.of('UTC'))
    def days = ChronoUnit.DAYS.between(tagTs, now) 

    return days
}

// Get the latest release tag from the binaries repo
def getLatestBinariesTag(String version) {
    def binariesRepo = "https://github.com/${params.BINARIES_REPO}".replaceAll("_NN_", version)

    def latestTag = sh(returnStdout: true, script:"git ls-remote --sort=-v:refname --tags ${binariesRepo} | grep '\\-ea\\-beta' | grep -v '\\^{}' | tr -s '\\t ' ' ' | cut -d' ' -f2 | sed 's,refs/tags/,,' | sort -V -r | head -1 | tr -d '\\n'")
    echo "latest jdk${version} binaries repo tag = ${latestTag}"

    return latestTag    
}

// Check if a given beta EA pipeline build is inprogress?
def isBuildInProgress(String pipelineName, String publishName) {
    def inProgress = false

    def pipeline = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getBuildHistory?buildName=${pipelineName}")
    def pipelineJson = new JsonSlurper().parseText(pipeline)
    if (pipelineJson.size() > 0) {
        pipelineJson.each { job ->
            def overridePublishName = ""

            job.buildParams.each { buildParam ->
                if (buildParam.name == "overridePublishName") {
                    overridePublishName = buildParam.value
                }
            }

            // Is job for the required tag and currently inprogress?
            if (overridePublishName == publishName && job.status != null && job.status.equals('Streaming')) {
                inProgress = true
            }
        }
    }

    return inProgress
}

// Verify the given release contains all the expected assets
def verifyReleaseContent(String version, String release, String variant, Map status) {
    echo "Verifying ${version} asserts in release: ${release}"
    status['assets'] = "Error"

    def escRelease = release.replaceAll("\\+", "%2B")
    def releaseAssetsUrl = "https://api.github.com/repos/${params.BINARIES_REPO}/releases/tags/${escRelease}".replaceAll("_NN_", version.replaceAll("u","").replaceAll("jdk",""))

    // Transform to browser URL for use in Slack message link
    status['assetsUrl'] = releaseAssetsUrl.replaceAll("api.github.com","github.com").replaceAll("/repos/","/").replaceAll("/tags/","/")

    // Get list of assets, concatenate into a single string
    def rc = sh(script: 'rm -f releaseAssets.json && curl -L -o releaseAssets.json '+releaseAssetsUrl, returnStatus: true)
    def releaseAssets = ""
    if (rc == 0) {
        releaseAssets = sh(script: "cat releaseAssets.json | grep '\"name\"' | tr '\\n' '#'", returnStdout: true)
    }
    if (releaseAssets == "") {
        echo "Error loading release assets list for ${releaseAssetsUrl}"
        status['assets'] = "Error loading ${releaseAssetsUrl}"
    } else {
        def configFile = "${version}.groovy"   
        def targetConfigPath = "${params.BUILD_CONFIG_URL}/${configFile}"
        echo "    Loading pipeline config file: ${targetConfigPath}"
        rc = sh(script: "curl -LO ${targetConfigPath}", returnStatus: true)
        if (rc != 0) {
            echo "Error loading ${targetConfigPath}"
            status['assets'] = "Error loading ${targetConfigPath}"
        } else {
            // Load the targetConfiguration
            targetConfigurations = null
            load configFile

            // Map of config architecture to artifact name
            def archToAsset = [x64Linux:       "x64_linux",
                               x64Windows:     "x64_windows",
                               x64Mac:         "x64_mac",
                               x64AlpineLinux: "x64_alpine-linux",
                               ppc64Aix:       "ppc64_aix",
                               ppc64leLinux:   "ppc64le_linux",
                               s390xLinux:     "s390x_linux",
                               aarch64Linux:   "aarch64_linux",
                               aarch64AlpineLinux: "aarch64_alpine-linux",
                               aarch64Mac:     "aarch64_mac",
                               arm32Linux:     "arm_linux",
                               x32Windows:     "x86-32_windows",
                               x64Solaris:     "x64_solaris",
                               sparcv9Solaris: "sparcv9_solaris"
                              ]
                               
            def missingAssets = []
            def foundAtLeastOneAsset = false
            targetConfigurations.keySet().each { osarch ->
                def variants = targetConfigurations[osarch]
                if (!variants.contains(variant)) {
                    return // variant not built for this osarch
                }
                echo "Verifying : $osarch"
                def foundAsset = false
                def missingForArch = []

                def imagetypes = ["debugimage", "jdk", "jre", "sbom"]
                if (version != "jdk8u") {
                    imagetypes.add("static-libs")
                    imagetypes.add("testimage")
                }

                // Work out the filetypes
                def filetypes
                def jdkjre_filetypes
                if (osarch.contains("Windows")) {
                    filetypes =        ["\\.zip", "\\.zip\\.json", "\\.zip\\.sha256\\.txt", "\\.zip\\.sig"]
                    jdkjre_filetypes = ["\\.msi", "\\.msi\\.json", "\\.msi\\.sha256\\.txt", "\\.msi\\.sig"]
                    jdkjre_filetypes.addAll(filetypes)
                } else if (osarch.contains("Mac")) {
                    filetypes        = ["\\.tar\\.gz", "\\.tar\\.gz\\.json", "\\.tar\\.gz\\.sha256\\.txt", "\\.tar\\.gz\\.sig"]
                    jdkjre_filetypes = ["\\.pkg", "\\.pkg\\.json", "\\.pkg\\.sha256\\.txt", "\\.pkg\\.sig"]
                    jdkjre_filetypes.addAll(filetypes)
                } else {
                    filetypes = ["\\.tar\\.gz", "\\.tar\\.gz\\.json", "\\.tar\\.gz\\.sha256\\.txt", "\\.tar\\.gz\\.sig"]
                    jdkjre_filetypes = filetypes
                }
                def sbom_filetypes = ["\\.json", "\\.json\\.sig", "-metadata\\.json"]

                imagetypes.each { image ->
                    // Find the file type for this image
                    def ftypes                     
                    if (image == "jdk" || image == "jre") {
                        ftypes = jdkjre_filetypes
                    } else if (image == "sbom") {
                        ftypes = sbom_filetypes
                    } else {
                        ftypes = filetypes
                    }

                    // If static-libs image then append -glibc or -musl accordingly 
                    def file_image = image
                    if (image == "static-libs" && osarch.contains("Linux")) {
                        if (osarch.contains("Alpine")) {
                            file_image="${file_image}-musl"
                        } else {
                            file_image="${file_image}-glibc"
                        }
                    }
                    // Search for artifacts in the releaseAssets list
                    ftypes.each { ftype ->
                        def arch_fname = archToAsset[osarch]
                        def findAsset = releaseAssets =~/.*${file_image}_${arch_fname}_[^"]*${ftype}".*/
                        if (!findAsset) {
                            missingForArch.add("$osarch : $image : $ftype".replaceAll("\\\\", ""))
                        } else {
                            foundAsset = true
                            foundAtLeastOneAsset = true
                        }
                    }
                }
                if (!foundAsset) {
                    echo "    $osarch : All artifacts missing"
                    missingAssets.add("$osarch : All : .All")
                } else if (missingForArch.size() > 0) {
                    echo "    $osarch : Missing artifacts: ${missingForArch}"
                    missingAssets.addAll(missingForArch)
                } else {
                    echo "    $osarch : Complete"
                }
            }

            // Set overall assets status for this release
            if (missingAssets.size() > 0) {
                if (foundAtLeastOneAsset) {
                    status['assets'] = "Missing artifacts"
                } else {
                    status['assets'] = "Missing ALL artifacts"
                }
                status['missingAssets'] = missingAssets
            } else {
                status['assets'] = "Complete"
            }
        }
    }
}

node('worker') {
  try{
    def variant = "${params.VARIANT}"
    def trssUrl    = "${params.TRSS_URL}"
    def apiUrl    = "${params.API_URL}"
    def slackChannel = "${params.SLACK_CHANNEL}"
    def featureReleases = "${params.FEATURE_RELEASES}".split("[, ]+") // feature versions 
    def tipRelease      = "${params.TIP_RELEASE}".trim() // Current jdk(head) version
    def nightlyStaleDays = "${params.MAX_NIGHTLY_STALE_DAYS}"
    def amberBuildAlertLevel = params.AMBER_BUILD_ALERT_LEVEL ? params.AMBER_BUILD_ALERT_LEVEL as Integer : -99
    def amberTestAlertLevel  = params.AMBER_TEST_ALERT_LEVEL  ? params.AMBER_TEST_ALERT_LEVEL as Integer : -99
    def nonTagBuildReleases = "${params.NON_TAG_BUILD_RELEASES}".split("[, ]+")

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
            // Check the binary is published
            // The release asset list is also verified
            featureReleases.each { featureRelease ->
              def featureReleaseInt = featureRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
              def assets = sh(returnStdout: true, script: "wget -q -O - '${apiUrl}/v3/assets/feature_releases/${featureReleaseInt}/ea?image_type=jdk&sort_method=DATE&pages=1&jvm_impl=${apiVariant}'")
              def assetsJson = new JsonSlurper().parseText(assets)

              def foundNonEvaluationBinaries = false
              def i=0
              while(!foundNonEvaluationBinaries && i < assetsJson.size()) {
                def releaseName = assetsJson[i].release_name
                def status = []
                if (nonTagBuildReleases.contains(featureReleaseInt)) {
                  // A non tag build, eg.a scheduled build for Oracle managed STS versions
                  def ts = assetsJson[i].timestamp // newest timestamp of a jdk asset
                  def assetTs = Instant.parse(ts).atZone(ZoneId.of('UTC'))
                  def now = ZonedDateTime.now(ZoneId.of('UTC'))
                  def days = ChronoUnit.DAYS.between(assetTs, now)
                  status = [releaseName: releaseName, maxStaleDays: nightlyStaleDays, actualDays: days]
                } else {
                  def latestOpenjdkBuild = getLatestOpenjdkBuildTag(featureRelease)
                  status = [releaseName: releaseName, expectedReleaseName: "${latestOpenjdkBuild}-ea-beta"]
                }

                // Verify the given release contains all the expected assets
                verifyReleaseContent(featureRelease, releaseName, variant, status)
                echo "  ${featureRelease} release binaries verification: "+status['assets']
                if (status['assets'] == "Missing ALL artifacts") {
                  echo "Published ${releaseName} binaries has no non-evaluation artifacts, it must be an 'evaluation' build, skip to next.."
                  i += 1
                } else {
                  foundNonEvaluationBinaries = true
                  healthStatus[featureReleaseInt] = status
                }
              }
            }

            // Check tip_release status, by querying binaries repo as API does not server the "tip" dev release
            if (tipRelease != "") {
              def latestOpenjdkBuild = getLatestOpenjdkBuildTag("jdk")
              def tipVersion = tipRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
              def releaseName = getLatestBinariesTag("${tipVersion}")
              status = [releaseName: releaseName, expectedReleaseName: "${latestOpenjdkBuild}-ea-beta"]
              verifyReleaseContent(tipRelease, releaseName, variant, status)
              echo "  ${tipRelease} release binaries verification: "+status['assets']
              healthStatus[tipVersion] = status
            }
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

        // Create list of build pipelines of interest based on the requests release versions
        def pipelinesOfInterest = ""
        def allReleases = []
        allReleases.addAll(featureReleases)
        if (tipRelease != "") {
            allReleases.add(tipRelease)
        }
        allReleases.each { release ->
           def featureReleaseStr = release.replaceAll("u", "").replaceAll("jdk", "")

           // Only interested in nightly/triggered openjdkNN-pipeline's
           pipelinesOfInterest += ",openjdk${featureReleaseStr}-pipeline"
        }

        // Get top level builds names
        def trssBuildNames = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getTopLevelBuildNames?type=Test")
        def buildNamesJson = new JsonSlurper().parseText(trssBuildNames)
        buildNamesJson.each { build ->
            // Is it a build Pipeline?
            if (build._id.buildName.contains('-pipeline')) {
                echo "Pipeline ${build._id.buildName}"
                def pipelineName = build._id.buildName

                // Are we interested in this pipeline?
                if (pipelinesOfInterest.contains(pipelineName)) {
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

        echo "======> Latest pipeline build Success Rating for variant: ${variant}"
        echo "======> Total number of Build jobs    = ${totalBuildJobs}"
        echo "======> Total number of Test jobs     = ${totalTestJobs}"
        echo "======> Build Success Rating  = ${nightlyBuildSuccessRating.intValue()} %"
        echo "======> Test Success Rating   = ${nightlyTestSuccessRating.intValue()} %"
        echo "======> Overall Latest Build & Test Success Rating = ${overallNightlySuccessRating} %"

        def statusColor = 'good'
        if (nightlyBuildSuccessRating.intValue() < amberBuildAlertLevel || nightlyTestSuccessRating.intValue() < amberTestAlertLevel) {
            statusColor = 'warning'
        }

        // Slack message:
        slackSend(channel: slackChannel, color: statusColor, message: 'Adoptium Latest Builds Success : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>')

echo 'Adoptium Latest Builds Success : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>'
    }

    stage('printPublishStats') {
        if (variant == 'temurin' || variant == 'hotspot') { //variant == "hotspot" should be enough for now. Keep temurin for later.
            echo '-------------- Latest pipeline health report ------------------'
            def allReleases = []
            allReleases.addAll(featureReleases)
            if (tipRelease != "") {
                allReleases.add(tipRelease)
            }
            allReleases.each { featureRelease ->
                def featureReleaseInt = featureRelease.replaceAll("u", "").replaceAll("jdk", "").toInteger()
                def status = healthStatus[featureReleaseInt]

                def slackColor = 'good'
                def health = "Healthy"
                def errorMsg = ""
                def releaseName = status['releaseName']
                def lastPublishedMsg = ""

                // Is it a non-tag triggered build? eg.Oracle STS version
                if (nonTagBuildReleases.contains(featureReleaseInt)) {
                    // Check for stale published build
                    def days = status['actualDays'] as int
                    lastPublishedMsg = "\nPublished: ${days} day(s) ago." // might actually be days + N hours, where N < 24
                    if (status['actualDays'] == 0) {
                        lastPublishedMsg = "\nPublished: less than 24 hours ago."
                    }
                    def maxDays = status['maxStaleDays'] as int
                    if (maxDays <= days) {
                        slackColor = 'warning'
                        health = "Unhealthy"
                        errorMsg = "\nStale threshold: ${maxDays} days."
                    }
                } else {
                    // Check latest published binaries are for the latest openjdk build tag
                    if (status['releaseName'] != status['expectedReleaseName']) {
                        def upstreamTagAge    = getOpenjdkBuildTagAge(featureRelease, status['expectedReleaseName'].replaceAll("-ea-beta", ""))
                        def isBuildInProgress = isBuildInProgress("openjdk${featureReleaseInt}-pipeline", status['expectedReleaseName'].replaceAll("-beta", ""))
                        if (upstreamTagAge > 3 && !isBuildInProgress)
                            slackColor = 'danger'
                            health = "Unhealthy"
                            errorMsg = "\nLatest Adoptium publish binaries "+status['releaseName']+" != latest upstream openjdk build "+status['expectedReleaseName']+" published ${upstreamTagAge} days ago. No build is in progress."
                        else {
                            errorMsg = "\nLatest upstream openjdk build "+status['expectedReleaseName']+" published ${upstreamTagAge} days ago. Build is in progress."
                        }
                    }
                }

                // Verify if any artifacts missing?                    
                def missingAssets = []
                if (status['assets'] != 'Complete') {
                    slackColor = 'danger'
                    health = "Unhealthy"
                    errorMsg += "\nArtifact status: "+status['assets']
                    missingAssets = status['missingAssets']
                }
                 
                // Print out formatted missing artifacts if any missing
                def missingMsg = ""
                if (missingAssets.size() > 0) {
                    missingMsg += " :"
                    // Collate by arch, array is sequenced by architecture
                    def archName = ""
                    def missingFiles = ""
                    missingAssets.each { missing ->
                        // arch : imageType : fileType
                        def missingFile = missing.split("[ :]+")
                        if (missingFile[0] != archName) {
                            if (archName != "") {
                                missingMsg += "\n    *${archName}*: ${missingFiles}"
                                echo "===> ${missingMsg}"
                            }
                            archName = missingFile[0]
                            missingFiles = missingFile[1]+missingFile[2]
                        } else {
                            missingFiles += ", "+missingFile[1]+missingFile[2]
                        }                        
                    } 
                    if (missingFiles != "") {
                        missingMsg += "\n    *${archName}*: ${missingFiles}"
                        echo "===> ${missingMsg}"
                    }
                }

                def releaseLink = "<" + status['assetsUrl'] + "|${releaseName}>"
                def fullMessage = "${featureRelease} latest pipeline publish status: *${health}*. Build: ${releaseLink}.${lastPublishedMsg}${errorMsg}${missingMsg}"
                echo "===> ${fullMessage}"
                //slackSend(channel: slackChannel, color: slackColor, message: fullMessage)
            }
            echo '----------------------------------------------------------------'
        }
    }
  } finally { 
    cleanWs notFailBuild: true
  } 
}

