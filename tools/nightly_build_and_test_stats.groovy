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

/* groovylint-disable NestedBlockDepth */

import groovy.json.JsonSlurper
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

def getPlatformConversionMap() {
    // A map to convert from a standard platform format to the variants used by build and test job names on Jenkins.
    def platformConversionMap = [x64Linux:           ["linux-x64", "x86-64_linux"],
                                 x64Windows:         ["windows-x64", "x86-64_windows"],
                                 x64Mac:             ["mac-x64", "x86-64_mac"],
                                 x64AlpineLinux:     ["alpine-linux-x64", "x86-64_alpine-linux"],
                                 ppc64Aix:           ["aix-ppc64", "ppc64_aix"],
                                 ppc64leLinux:       ["linux-ppc64le", "ppc64le_linux"],
                                 s390xLinux:         ["linux-s390x", "s390x_linux"],
                                 aarch64Linux:       ["linux-aarch64", "aarch64_linux"],
                                 aarch64AlpineLinux: ["alpine-linux-aarch64", "aarch64_alpine-linux"],
                                 aarch64Mac:         ["mac-aarch64", "aarch64_mac"],
                                 arm32Linux:         ["linux-arm", "arm_linux"],
                                 x32Windows:         ["windows-x86-32", "x86-32_windows"],
                                 x64Solaris:         ["solaris-x64", "x64_solaris"],
                                 sparcv9Solaris:     ["solaris-sparcv9", "sparcv9_solaris"],
                                 riscv64Linux:       ["linux-riscv64", "riscv64_linux"]
                                ]
    return platformConversionMap
}

def getPlatformReproTestMap() {
    // A map to return the test bucket and test name for the repducibile platforms
    def platformReproTestMap = [x64Linux:           ["special.system", "Rebuild_Same_JDK_Reproducibility_Test"],
                                x64Windows:         ["dev.system", "Rebuild_Same_JDK_Reproducibility_Test_win"],
                                x64Mac:             ["NA", ""],
                                ppc64leLinux:       ["NA", ""],
                                aarch64Linux:       ["NA", ""],
                                aarch64Mac:         ["NA", ""]
                               ]
    return platformReproTestMap
}

// Check if the given tag is a -ga tag ?
def isGaTag(String version, String tag) {
    if (version == "${params.TIP_RELEASE}".trim()) {
        // Tip release has no GA tags
        return false
    }

    def openjdkRepo = getUpstreamRepo(version) 

    def tagCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${tag}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")

    def gaCheckTag = "unknown"
    if (version.contains("jdk8u")) {
        if (tag.indexOf("-") > 0) {
            gaCheckTag = tag.substring(0, tag.indexOf("-"))+"-ga"
        }
    } else {
        if (tag.indexOf("+") > 0) {
            gaCheckTag = tag.substring(0, tag.indexOf("+"))+"-ga"
        }
    }
    def gaCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${gaCheckTag}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")

    if (gaCommitSHA != "" && tagCommitSHA == gaCommitSHA) {
        return true
    } else {
        return false
    }
}

// Determine the upstream OpenJDK repository
def getUpstreamRepo(String version) {
    def openjdkRepo

    def versionInt = (version == "aarch32-jdk8u" || version == "alpine-jdk8u") ? 8 : version.replaceAll("[a-z]","").toInteger()
    def isUpdateVersion = version.endsWith("u")
    
    if (versionInt >= 23 && !isUpdateVersion) {
        // jdk-23+ stabalisation versions are now branches in jdk(head) repo
        openjdkRepo = "https://github.com/openjdk/jdk.git"
    } else if (version == "aarch32-jdk8u") {
        openjdkRepo = "https://github.com/openjdk/aarch32-port-jdk8u.git"
    } else if (version.contains("jdk8u")) {
        openjdkRepo = "https://github.com/openjdk/jdk8u.git"
    } else {
        openjdkRepo = "https://github.com/openjdk/${version}.git"
    }

    return openjdkRepo
}

// Get the latest upstream openjdk build tag
def getLatestOpenjdkBuildTag(String version) {
    def openjdkRepo = getUpstreamRepo(version)
    def versionInt = (version == "aarch32-jdk8u" || version == "alpine-jdk8u") ? 8 : version.replaceAll("[a-z]","").toInteger()

    def tagFilter
    if (version == "aarch32-jdk8u") {
        tagFilter = "| grep 'jdk8u.*-aarch32-'"
    } else if (version.contains("jdk8u")) {
        tagFilter = "| grep 'jdk8u'"
    } else {
        tagFilter = "| grep 'jdk-"+versionInt+"[\\.\\+]'"
    }

    def latestTag = sh(returnStdout: true, script:"git ls-remote --sort=-v:refname --tags ${openjdkRepo} | grep -v '\\^{}' | tr -s '\\t ' ' ' | cut -d' ' -f2 | sed \"s,refs/tags/,,\" | grep -v '\\+0' | grep -v '\\-b00' | grep -v '\\-ga' ${tagFilter} | sort -V -r | head -1 | tr -d '\\n'")
    echo "latest upstream openjdk/${version} tag = ${latestTag}"

    return latestTag
}

// How long ago was the given upstream tag published?
def getOpenjdkBuildTagAge(String version, String tag) {
    def openjdkRepo = getUpstreamRepo(version)

    def date = sh(returnStdout: true, script:"(rm -rf tmpRepo; git clone ${openjdkRepo} tmpRepo; cd tmpRepo; git for-each-ref --format=\"%(refname:short) %(creatordate:format:%Y-%m-%dT%H:%M:%S%z)\" \"refs/tags/*\"; cd ..; rm -rf tmpRepo) | grep \"${tag}\" | cut -d\" \" -f2 | sed -e 's/.\\{22\\}/&:/1' | tr -d '\\n'")

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

// Return our best guess at the url that generated a specific build.
def getBuildUrl(String trssUrl, String variant, String featureRelease, String publishName, String scmRef) {
    def functionBuildUrl = ["", "", ""]

    def featureReleaseInt = (featureRelease == "aarch32-jdk8u" || featureRelease == "alpine-jdk8u") ? 8 : featureRelease.replaceAll("[a-z]","").toInteger()
    def pipelineName = "openjdk${featureReleaseInt}-pipeline"

    def pipeline = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getBuildHistory?buildName=${pipelineName}")
    def pipelineJson = new JsonSlurper().parseText(pipeline)
    if (pipelineJson.size() > 0) {
        pipelineJson.each { job ->
            def overridePublishName = ""
            def buildScmRef = ""
            def containsX64AlpineLinux = false
            def containsVariant = false

            job.buildParams.each { buildParam ->
                if (buildParam.name == "overridePublishName") {
                    overridePublishName = buildParam.value
                } else if (buildParam.name == "scmReference") {
                    buildScmRef = buildParam.value
                } else if (buildParam.name == "targetConfigurations") {
                    containsX64AlpineLinux = (buildParam.value.contains("x64AlpineLinux"))
                    containsVariant        = (buildParam.value.contains(variant))
                }
            }

            // Is there a job for the required tag?
            if (containsVariant && overridePublishName == publishName && buildScmRef == scmRef && job.status != null) {
                if (featureReleaseInt == 8) {
                    // alpine-jdk8u cannot be distinguished from jdk8u by the scmRef alone, so check for "x64AlpineLinux" in the targetConfiguration
                    if ((featureRelease == "alpine-jdk8u" && containsX64AlpineLinux) || (featureRelease != "alpine-jdk8u" && !containsX64AlpineLinux)) {
                        functionBuildUrl = [job.buildUrl, job._id, job.status]
                    }
                } else {
                    functionBuildUrl = [job.buildUrl, job._id, job.status]
                    echo "Found "+featureRelease+" pipeline with this ID: "+job._id
                }
            }
        }
    }

    return functionBuildUrl
}

// Verify the given release contains all the expected assets
def verifyReleaseContent(String version, String release, String variant, Map status) {
    echo "Verifying ${version} assets in release: ${release}"
    status['assets'] = "Error"
    status['missingAssets'] = []

    def configVersion = version
    // aarch32-jdk8u and alpine-jdk8u use "jdk8u" config
    if (version == "aarch32-jdk8u" || version == "alpine-jdk8u") {
        configVersion = "jdk8u"
    }

    def escRelease = release.replaceAll("\\+", "%2B")
    def releaseAssetsUrl = "https://api.github.com/repos/${params.BINARIES_REPO}/releases/tags/${escRelease}".replaceAll("_NN_", configVersion.replaceAll("[a-z]",""))

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
        def configFile = "${configVersion}.groovy"

        targetConfigurations = null
        // aarch32-jdk8u and alpine-jdk8u are single configurations
        if (version == "aarch32-jdk8u") {
            targetConfigurations = [:]
            targetConfigurations['arm32Linux'] = [variant]
        } else if (version == "alpine-jdk8u") {
            targetConfigurations = [:]
            targetConfigurations['x64AlpineLinux'] = [variant]
        } else {
            def targetConfigPath = "${params.BUILD_CONFIG_URL}/${configFile}"
            echo "    Loading pipeline config file: ${targetConfigPath}"
            rc = sh(script: "curl -LO ${targetConfigPath}", returnStatus: true)
            if (rc != 0) {
                echo "Error loading ${targetConfigPath}"
                status['assets'] = "Error loading ${targetConfigPath}"
            } else {
                // Load the targetConfiguration
                load configFile
            }
        }

        if (targetConfigurations) {
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
                               sparcv9Solaris: "sparcv9_solaris",
                               riscv64Linux:   "riscv64_linux"
                              ]
                               
            def missingAssets = []
            def foundAtLeastOneAsset = false
            targetConfigurations.keySet().each { osarch ->
              if (version == "jdk8u" && (osarch == "arm32Linux" || osarch == "x64AlpineLinux")) {
                echo "ignoring jdk8u:$osarch is built in a unique feature release"
              } else {
                def variants = targetConfigurations[osarch]
                if (!variants.contains(variant)) {
                    return // variant not built for this osarch
                }
                echo "Verifying : $osarch"
                def foundAsset = false
                def missingForArch = []

                def imagetypes = ["debugimage", "jdk", "jre", "sbom"]
                if (!version.contains("jdk8u")) {
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

// For a given pipeline, tell us how reproducible the builds were.
// Note: Will limit itself to jdk versions and platforms in the results Map.
def getReproducibilityPercentage(String jdkVersion, String trssId, String trssURL, Map results) {
    echo "Called repro method with trssID:"+trssId

    def platformConversionMap = getPlatformConversionMap()
    def platformReproTestMap = getPlatformReproTestMap()

    // We are only looking for reproducible percentages for the relevant jdk versions...
    if ( trssId != "" && results.containsKey(jdkVersion) ) {
        def jdkVersionInt = jdkVersion.replaceAll("[a-z]", "")

        // ...and platforms.
        results[jdkVersion][1].each { onePlatform, valueNotUsed ->
            // If this platform doesn't have a reproducibility test yet, skip it.
            if (platformReproTestMap[onePlatform][0].equals("NA")) {
                results[jdkVersion][1][onePlatform] = "NA"
                // Then we exit this lambda and skip to the next platform.
                return
            }

            def pipelineLink = trssURL+"/api/getAllChildBuilds?parentId="+trssId+"\\&buildNameRegex=^"+jdkVersion+"\\-"+platformConversionMap[onePlatform][0]+"\\-temurin\$"
            def trssBuildJobNames = sh(returnStdout: true, script: "wget -q -O - ${pipelineLink}")
            def platformResult = "???% - Build not found. Pipeline link: " + pipelineLink

            // Does this platform have a build in this pipeline?
            if ( trssBuildJobNames.length() > 10 ) {
                def buildJobNamesJson = new JsonSlurper().parseText(trssBuildJobNames)

                // For each build, search the test output for the unit test we need, then look for reproducibility percentage.
                buildJobNamesJson.each { buildJob ->
                    platformResult = "???% - Build found, but no reproducibility tests. Build link: " + buildJob.buildUrl
                    def testPlatform = platformConversionMap[onePlatform][1]
                    def reproTestName=platformReproTestMap[onePlatform][1]
                    def reproTestBucket=platformReproTestMap[onePlatform][0]
                    def testJobTitle="Test_openjdk${jdkVersionInt}_hs_${reproTestBucket}_${testPlatform}.*"
                    def trssTestJobNames = sh(returnStdout: true, script: "wget -q -O - ${trssURL}/api/getAllChildBuilds?parentId=${buildJob._id}\\&buildNameRegex=^${testJobTitle}\$")

                    // Did this build have tests?
                    if ( trssTestJobNames.length() > 10 ) {
                        platformResult = "???% - Found ${reproTestBucket}, but did not find ${reproTestName}. Build Link: " + buildJob.buildUrl
                        def testJobNamesJson = new JsonSlurper().parseText(trssTestJobNames)

                        // For each test job (including testList subjobs), we now search for the reproducibility test.
                        testJobNamesJson.each { testJob ->
                            def testOutput = sh(returnStdout: true, script: "wget -q -O - ${testJob.buildUrl}/consoleText")

                            // If we can find it, then we look for the anticipated percentage.
                            if ( testOutput.contains("Running test "+reproTestName) ) {
                                platformResult = "???% - ${reproTestName} ran but failed to produce a percentage. Test Link: " + testJob.buildUrl
                                // Now we know the test ran, 
                                def matcherObject = testOutput =~ /ReproduciblePercent = [0-9]+ %/
                                if ( matcherObject ) {
                                    platformResult = matcherObject[0] =~ /[0-9]+ %/
                                }
                            }
                        }
                    }
                }
            }
            results[jdkVersion][1][onePlatform] = platformResult
        }

        // Now we have the percentages for each platform, we canculate the jdkVersion-specific average.
        def overallAverage = 0
        // Ignoring the platforms where the test is not available yet.
        def naCount = 0
        results[jdkVersion][1].each{key, value ->
            if (value.equals("NA")) {
                naCount++
            } else if ( (value ==~ /^[0-9]+ %/) ) {
                overallAverage += (value =~ /^[0-9]+/)[0] as Integer
            }
            // else do nothing, as we presume non-integer and non-NA values are 0.
        }
        overallAverage = overallAverage == 0 ? 0 : overallAverage.intdiv(results[jdkVersion][1].size() - naCount)
        results[jdkVersion][0] = overallAverage+" %"
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

    def healthStatus = [:]
    def testStats = []

    // Specifies what JDK versions and platforms are expected to be reproducible.
    // The "?" symbols will soon be replaced by reproducibility percentages.
    // Layout: [jdkVersion: [Overall-reproducibility, [By-platform reproducibility breakdown]]]
    def reproducibleBuilds = ["jdk21u": [ "?", ["x64Linux": "?", "aarch64Linux": "?", "ppc64leLinux": "?", "x64Windows": "?", "x64Mac": "?", "aarch64Mac": "?"]]]

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
            def allNonTipReleases = []
            allNonTipReleases.addAll(featureReleases)
            if ("${params.NON_TAG_BUILD_RELEASES}".trim() != "") {
                allNonTipReleases.addAll(nonTagBuildReleases)
            }
            allNonTipReleases.each { featureRelease ->
              def featureReleaseInt = (featureRelease == "aarch32-jdk8u" || featureRelease == "alpine-jdk8u") ? 8 : featureRelease.replaceAll("[a-z]","").toInteger()

              // Extra filter to find latest jdk8u port assets
              def extraFilter = ""
              if (featureRelease == "aarch32-jdk8u") {
                  extraFilter = "architecture=arm&os=linux&"
              } else if (featureRelease == "alpine-jdk8u") {
                  extraFilter = "architecture=x64&os=alpine-linux&"
              }

              def assets = sh(returnStdout: true, script: "wget -q -O - '${apiUrl}/v3/assets/feature_releases/${featureReleaseInt}/ea?${extraFilter}image_type=jdk&sort_method=DATE&pages=1&jvm_impl=${apiVariant}'")
              def assetsJson = new JsonSlurper().parseText(assets)

              def status = []
              if (assetsJson.size() > 0) {
                def releaseName = assetsJson[0].release_name
                if (nonTagBuildReleases.contains(featureRelease)) {
                  // A non tag build, eg.a scheduled build for Oracle managed STS versions
                  def latestOpenjdkBuild = getLatestOpenjdkBuildTag(featureRelease)
                  def ts = assetsJson[0].timestamp // newest timestamp of a jdk asset
                  def assetTs = Instant.parse(ts).atZone(ZoneId.of('UTC'))
                  def now = ZonedDateTime.now(ZoneId.of('UTC'))
                  def days = ChronoUnit.DAYS.between(assetTs, now)
                  status = [releaseName: releaseName, maxStaleDays: nightlyStaleDays, actualDays: days, upstreamTag: latestOpenjdkBuild]
                } else {
                  def latestOpenjdkBuild = getLatestOpenjdkBuildTag(featureRelease)
                  def expectedReleaseName = "${latestOpenjdkBuild}-ea-beta"
                  if (featureRelease == "aarch32-jdk8u") {
                      expectedReleaseName = latestOpenjdkBuild.substring(0, latestOpenjdkBuild.indexOf("-aarch32"))+"-ea-beta"
                  }
                  status = [releaseName: releaseName, expectedReleaseName: expectedReleaseName, upstreamTag: latestOpenjdkBuild]
                }

                // Verify the given release contains all the expected assets
                verifyReleaseContent(featureRelease, releaseName, variant, status)
                echo "  ${featureRelease} release binaries verification: "+status['assets']
                healthStatus[featureRelease] = status
              } else {
                status['assets'] = "Unable to find any releases"
                echo "  ${featureRelease} release binaries verification: "+status['assets']
              }
            }

            // Check tip_release status, by querying binaries repo as API does not server the "tip" dev release
            if (tipRelease != "") {
              def latestOpenjdkBuild = getLatestOpenjdkBuildTag(tipRelease)
              def tipVersion = tipRelease.replaceAll("[a-z]","").toInteger()
              def releaseName = getLatestBinariesTag("${tipVersion}")
              status = [releaseName: releaseName, expectedReleaseName: "${latestOpenjdkBuild}-ea-beta", upstreamTag: latestOpenjdkBuild]
              verifyReleaseContent(tipRelease, releaseName, variant, status)
              echo "  ${tipRelease} release binaries verification: "+status['assets']
              healthStatus[tipRelease] = status
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
        if ("${params.NON_TAG_BUILD_RELEASES}".trim() != "") {
           allReleases.addAll(nonTagBuildReleases)
        }
        allReleases.each { release ->
           def featureReleaseStr = (release == "aarch32-jdk8u" || release == "alpine-jdk8u") ? "8" : release.replaceAll("[a-z]","")

           // Only interested in triggered openjdkNN-pipeline's
           if (!pipelinesOfInterest.contains(",openjdk${featureReleaseStr}-pipeline")) {
               pipelinesOfInterest += ",openjdk${featureReleaseStr}-pipeline"
           }
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
                  // Find all the "Done" pipeline builds in the last 7 days, started by "timer", or upstream project "build-scripts/utils/betaTrigger_"
                  def pipeline = sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getBuildHistory?buildName=${pipelineName}")
                  def pipelineJson = new JsonSlurper().parseText(pipeline)
                  if (pipelineJson.size() > 0) {
                    // Find first in list started by "timer", "build-scripts/utils/betaTrigger_" or "build-scripts/utils/releaseTrigger_"
                    pipelineJson.each { job ->
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
                            // Report pipelines built within the last week
                            if (job.status != null && job.status.equals('Done') && job.startBy != null && days <= 7) {
                                if (job.startBy.startsWith('timer')) {
                                    // Timer scheduled job
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
                                } else if (job.startBy.startsWith("upstream project \"build-scripts/utils/betaTrigger_")) {
                                    // Beta build tag triggered build
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
                                } else if (job.startBy.startsWith("upstream project \"build-scripts/utils/releaseTrigger_")) {
                                    // Release build tag triggered build
                                    pipeline_id = job._id
                                    pipelineUrl = job.buildUrl
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
        slackSend(channel: slackChannel, color: statusColor, message: 'Adoptium last 7 days Overall Build Success Rating : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>')

        echo 'Adoptium last 7 days Overall Build Success Rating : *' + variant + '* => *' + overallNightlySuccessRating + '* %\n  Build Job Rating: ' + totalBuildJobs + ' jobs (' + nightlyBuildSuccessRating.intValue() + '%)  Test Job Rating: ' + totalTestJobs + ' jobs (' + nightlyTestSuccessRating.intValue() + '%) <' + BUILD_URL + '/console|Detail>'
    }

    stage('printPublishStats') {
        if (variant == 'temurin' || variant == 'hotspot') { //variant == "hotspot" should be enough for now. Keep temurin for later.
            echo '-------------- Latest pipeline health report ------------------'
            def allReleases = []
            allReleases.addAll(featureReleases)
            if (tipRelease != "") {
                allReleases.add(tipRelease)
            }
            if (("${params.NON_TAG_BUILD_RELEASES}".trim() != "")) {
               allReleases.addAll(nonTagBuildReleases)
            }
            allReleases.each { featureRelease ->
                def status = healthStatus[featureRelease]

                def slackColor = 'good'
                def health = "Healthy"
                def errorMsg = ""
                def releaseName = status['releaseName']
                def lastPublishedMsg = ""
                def probableBuildUrl = ""
                def probableBuildStatus = ""
                def probableBuildIdForTRSS = ""

                // Is it a non-tag triggered build? eg.Oracle STS version
                if (nonTagBuildReleases.contains(featureRelease)) {
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
                    // Check if build in-progress
                    (probableBuildUrl, probableBuildIdForTRSS, probableBuildStatus) = getBuildUrl(trssUrl, variant, featureRelease, status['expectedReleaseName'].replaceAll("-beta", ""), status['upstreamTag']+"_adopt")

                    // Check latest published binaries are for the latest openjdk build tag, unless upstream is a GA tag
                    if (status['releaseName'] != status['expectedReleaseName'] && !isGaTag(featureRelease, status['upstreamTag'])) {
                        def upstreamTagAge    = getOpenjdkBuildTagAge(featureRelease, status['upstreamTag'])
                        if (upstreamTagAge > 3 && probableBuildStatus == "Done") {
                            slackColor = 'danger'
                            health = "Unhealthy"
                            errorMsg = "\nLatest Adoptium publish binaries "+status['releaseName']+" != latest upstream openjdk build "+status['upstreamTag']+" published ${upstreamTagAge} days ago. *No build is in progress*."
                        } else {
                            if (probableBuildStatus == "Streaming") {
                                errorMsg = "\nLatest upstream openjdk build "+status['upstreamTag']+" published ${upstreamTagAge} days ago. <" + probableBuildUrl + "|Build is in progress>."
                            } else {
                                errorMsg = "\nLatest upstream openjdk build "+status['upstreamTag']+" published ${upstreamTagAge} days ago. *Build is awaiting 'trigger'*."
                            }
                        }
                    }

                    def testsShouldHaveRun = false
                    if ( probableBuildUrl != "" && sh(returnStdout: true, script: "wget -q -O - ${trssUrl}/api/getBuildHistory?buildUrl=${probableBuildUrl}").count("\\\"enableTests\\\",\\\"value\\\": true") == 1 ) {
                        testsShouldHaveRun = true
                    }
                    if (reproducibleBuilds.containsKey(featureRelease)) {
                        if (testsShouldHaveRun) {
                            getReproducibilityPercentage(featureRelease, probableBuildIdForTRSS, trssUrl, reproducibleBuilds)
                            if ( reproducibleBuilds[featureRelease][0] != "100%") {
                                slackColor = 'danger'
                                health = "Unhealthy"
                                def summaryOfRepros = ""
                                echo "Build reproducibility percentages for " + featureRelease + " did not add up to 100%. Breakdown: "
                                reproducibleBuilds[featureRelease][1].each{ key, value -> 
                                    if (!value.equals("NA")) {
                                        echo key+": "+value
                                        if(value ==~ /[0-9]+ %/) {
                                            summaryOfRepros+=" "+key+"("+value+"),"
                                        } else {
                                            summaryOfRepros+=" "+key+"(?%),"
                                        }
                                    } else {
                                        echo key+": NA - Reproducibility testing has not yet been implimented for this presumed reproducible build."
                                    }
                                }

                                //Remove trailing comma.
                                summaryOfRepros = summaryOfRepros.substring(0, summaryOfRepros.length() - 1);

                                errorMsg += "\nBuild repro summary: "+summaryOfRepros
                            }
                        } else {
                            // Ignore test results if the tests for this pipeline were intentionally disabled.
                            reproducibleBuilds[featureRelease][0] = "N/A - Tests disabled"
                        }
                    }
                }

                // Verify if any artifacts missing?
                def missingMsg = ""
                // Don't check if upstream tag is a GA, as the ea-beta will only be for evaluation platforms
                if (!isGaTag(featureRelease, status['upstreamTag'])) {
                    def missingAssets = []
                    if (status['assets'] != 'Complete') {
                        slackColor = 'danger'
                        health = "Unhealthy"
                        errorMsg += "\nArtifact status: "+status['assets']
                        if (probableBuildStatus == "Streaming") {
                            errorMsg += ", <" + probableBuildUrl + "|Build is in progress>"
                        } else {
                            errorMsg += ", *No build is in progress*"
                        }
                        missingAssets = status['missingAssets']
                    }

                    // Print out formatted missing artifacts if any missing
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
                }

                def reproducibilityText = "Reproducibility: N/A"
                if (reproducibleBuilds.containsKey(featureRelease)) {
                    reproducibilityText = "Reproducibility: "+reproducibleBuilds[featureRelease][0]
                }

                def releaseLink = "<" + status['assetsUrl'] + "|${releaseName}>"
                def fullMessage = "${featureRelease} latest 'EA Build' publish status: *${health}*. ${reproducibilityText} Build: ${releaseLink}.${lastPublishedMsg}${errorMsg}${missingMsg}"
                echo "===> ${fullMessage}"
                slackSend(channel: slackChannel, color: slackColor, message: fullMessage)
            }
            echo '----------------------------------------------------------------'
        }
    }
  } finally { 
    cleanWs notFailBuild: true
  } 
}

