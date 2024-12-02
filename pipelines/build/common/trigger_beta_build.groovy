package common
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

import java.nio.file.NoSuchFileException
import groovy.json.JsonOutput
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Month
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
  Detect new upstream OpenJDK source build tag, and trigger a "beta" pipeline build
  if the given build has not already been published, and the given version is
  not GA yet (existance of -ga tag).

  The "Force" option can be used to re-build and re-publish the existing latest build.
*/

def variant="${params.VARIANT}"
def mirrorRepo="${params.MIRROR_REPO}"
def version="${params.JDK_VERSION}".toInteger()
def binariesRepo="${params.BINARIES_REPO}"

def triggerMainBuild = false
def triggerEvaluationBuild = false
def enableTesting = true
def overrideMainTargetConfigurations = params.OVERRIDE_MAIN_TARGET_CONFIGURATIONS
def overrideEvaluationTargetConfigurations = params.OVERRIDE_EVALUATION_TARGET_CONFIGURATIONS
def ignore_platforms = "${params.IGNORE_PLATFORMS}" // platforms not to build

def mainTargetConfigurations       = overrideMainTargetConfigurations
def evaluationTargetConfigurations = overrideEvaluationTargetConfigurations

def latestAdoptTag
def publishJobTag

// Is the current day within the release period of from the previous Saturday to the following Sunday
// from the release Tuesday ?
def isDuringReleasePeriod() {
    def releasePeriod = false
    def now = ZonedDateTime.now(ZoneId.of('UTC'))
    def month = now.getMonth()

    // Is it a release month? CPU updates in Jan, Apr, Jul, Oct
    // New major versions are released in Mar and Sept
    if (month == Month.JANUARY || month == Month.MARCH || month == Month.APRIL || month == Month.JULY || month == Month.SEPTEMBER || month == Month.OCTOBER) {
        // Yes, calculate release Tuesday, which is the closest Tuesday to the 17th
        def day17th = now.withDayOfMonth(17)
        def dayOfWeek17th = day17th.getDayOfWeek()
        def releaseTuesday
        if (dayOfWeek17th == DayOfWeek.SATURDAY || dayOfWeek17th == DayOfWeek.SUNDAY || dayOfWeek17th == DayOfWeek.MONDAY || dayOfWeek17th == DayOfWeek.TUESDAY) {
            releaseTuesday = day17th.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        } else {
            releaseTuesday = day17th.with(TemporalAdjusters.previous(DayOfWeek.TUESDAY))
        }

        // Release period no trigger from prior week previous Saturday to following Sunday
        def days = ChronoUnit.DAYS.between(releaseTuesday, now)
        if (days >= -10 && days <= 5) {
            releasePeriod = true
        }
    }

    return releasePeriod
}

// Load the given targetConfigurations from the pipeline config
def loadTargetConfigurations(String javaVersion, String variant, String configSet, String ignore_platforms) {
    def targetConfigPath = "${params.BUILD_CONFIG_URL}"

    def to_be_ignored = ignore_platforms.split("[, ]+")
    targetConfigurations = null

    configFile = "jdk${javaVersion}${configSet}.groovy"
    def rc = sh(script: "curl --fail -LO ${targetConfigPath}/${configFile}", returnStatus: true)
    if (rc != 0) {
        echo "Error loading ${targetConfigPath}/${configFile}, trying ${targetConfigPath}/jdk${javaVersion}u${configSet}.groovy"
        configFile = "jdk${javaVersion}u${configSet}.groovy"
        rc = sh(script: "curl --fail -LO ${targetConfigPath}/${configFile}", returnStatus: true)
        if (rc != 0) {
            echo "Error loading ${targetConfigPath}/${configFile}"
        }
    }

    if (rc == 0) {
        // We successfully downloaded the pipeline config file, now load it into groovy to set targetConfigurations..
        load configFile
        echo "Successfully loaded ${targetConfigPath}/${configFile}"
    }

    def targetConfigurationsForVariant = [:]
    if (targetConfigurations != null) {
        targetConfigurations.each { platform ->
            if (platform.value.contains(variant) && !to_be_ignored.contains(platform.key)) {
                targetConfigurationsForVariant[platform.key] = [variant]
            }
        }
    }

    return targetConfigurationsForVariant
}

// Verify the given published release tag contains the given asset architecture
def checkJDKAssetExistsForArch(String binariesRepo, String version, String releaseTag, String arch) {
    def assetExists = false

    echo "Verifying ${version} JDK asset for ${arch} in release: ${releaseTag}"

    def escRelease = releaseTag.replaceAll("\\+", "%2B")
    def releaseAssetsUrl = binariesRepo.replaceAll("github.com","api.github.com/repos") + "/releases/tags/${escRelease}"

    // Get list of assets, concatenate into a single string
    def rc = sh(script: 'rm -f releaseAssets.json && curl -L -o releaseAssets.json '+releaseAssetsUrl, returnStatus: true)
    def releaseAssets = ""
    if (rc == 0) {
        releaseAssets = sh(script: "cat releaseAssets.json | grep '\"name\"' | tr '\\n' '#'", returnStdout: true)
    }

    if (releaseAssets == "") {
        echo "No release assets for ${releaseAssetsUrl}"
    } else {
        // Work out the JDK artifact filetype
        def filetype
        if (arch.contains("windows")) {
            filetype = "\\.zip"
        } else {
            filetype = "\\.tar\\.gz"
        }

        def findAsset = releaseAssets =~/.*jdk_${arch}_[^"]*${filetype}".*/
        if (findAsset) {
            assetExists = true
        }
    }

    if (assetExists) {
        echo "${arch} JDK asset for version ${version} tag ${releaseTag} exists"
    } else {
        echo "${arch} JDK asset for version ${version} tag ${releaseTag} NOT FOUND"
    }
    return assetExists
}

node('worker') {
    def adopt_tag_search
    if (version == 8) {
        // eg. jdk8u422-b03_adopt
        adopt_tag_search = 'grep "jdk8u.*_adopt"'
        if (mirrorRepo.contains("aarch32-jdk8u")) {
            adopt_tag_search = adopt_tag_search + ' | grep "\\-aarch32\\-"'
        }
    } else {
        // eg. jdk-11.0.24+6_adopt or jdk-23+26_adopt
        adopt_tag_search = 'grep "jdk-'+version+'[\\.\\+].*_adopt"'
    }

    // Find latest _adopt tag for this version?
    latestAdoptTag = sh(script:'git ls-remote --sort=-v:refname --tags "'+mirrorRepo+'" | grep -v "\\^{}" | grep -v "\\+0\\$" | grep -v "\\-ga\\$" | '+adopt_tag_search+' | tr -s "\\t " " " | cut -d" " -f2 | sed "s,refs/tags/,," | sort -V -r | head -1 | tr -d "\\n"', returnStdout:true)
    if (latestAdoptTag.indexOf("_adopt") < 0) {
        def error = "Error finding latest _adopt tag for ${mirrorRepo}"
        echo "${error}"
        throw new Exception("${error}")
    }
    echo "Latest Adoptium tag from ${mirrorRepo} = ${latestAdoptTag}"

    // publishJobTag is TAG that gets passed to the Adoptium "publish job"
    if (mirrorRepo.contains("aarch32-jdk8u")) {
        publishJobTag = latestAdoptTag.substring(0, latestAdoptTag.indexOf("-aarch32"))+"-ea"
    } else {
        publishJobTag = latestAdoptTag.replaceAll("_adopt","-ea")
    }
    echo "publishJobTag = ${publishJobTag}"

    // binariesRepoTag is the resulting published github binaries release tag created by the Adoptium "publish job"
    def binariesRepoTag = publishJobTag + "-beta"

    if (isDuringReleasePeriod()) {
        echo "We are within a release period (previous Saturday to the following Sunday around the release Tuesday), so testing is disabled."
        enableTesting = false
    }

    if (!params.FORCE_MAIN && !params.FORCE_EVALUATION) {
        // Determine this versions potential GA tag, so as to not build and publish a GA version
        def gaTag
        def versionStr
        if (version > 8) {
            versionStr = latestAdoptTag.substring(0, latestAdoptTag.indexOf("+"))
        } else {
            versionStr = latestAdoptTag.substring(0, latestAdoptTag.indexOf("-"))
        }
        gaTag=versionStr+"-ga"
        echo "Expected GA tag to check for = ${gaTag}"
   
        // If "-ga" tag exists, then we don't want to trigger a MAIN build 
        def gaTagCheck=sh(script:'git ls-remote --sort=-v:refname --tags "'+mirrorRepo+'" | grep -v "\\^{}" | grep "'+gaTag+'"', returnStatus:true)
        if (gaTagCheck == 0) {
            echo "Version "+versionStr+" already has a GA tag so not triggering a MAIN build"
        }

        // Check binaries repo for existance of the given release tag having being already built?
        def jdkAssetToCheck = "x64_linux"
        if (mirrorRepo.contains("aarch32-jdk8u")) {
            // aarch32-jdk8u built in its own pipeline
            jdkAssetToCheck = "arm_linux"
        } else if (mirrorRepo.contains("alpine-jdk8u")) {
            // alpine-jdk8u built in its own pipeline
            jdkAssetToCheck = "x64_alpine-linux"
        }

        echo "Checking if ${binariesRepoTag} is already published for JDK asset ${jdkAssetToCheck} ?"
        def assetExists = checkJDKAssetExistsForArch(binariesRepo, versionStr, binariesRepoTag, jdkAssetToCheck)

        if (assetExists) {
            echo "Build tag ${binariesRepoTag} is already published - nothing to do"
        } else {
            echo "New unpublished build tag ${binariesRepoTag} - triggering builds"
            if (gaTagCheck == 0) {
                echo "Version "+versionStr+" already has a GA tag so not triggering a MAIN build"
            } else {
                triggerMainBuild = true
            }
            triggerEvaluationBuild = true
        }
    } else {
        echo "FORCE triggering specified builds.."
        triggerMainBuild = params.FORCE_MAIN
        triggerEvaluationBuild = params.FORCE_EVALUATION
    }

    // If we are going to trigger, then load the targetConfigurations
    if (triggerMainBuild || triggerEvaluationBuild) {
        // Load the targetConfigurations
        if (triggerMainBuild && mainTargetConfigurations == "") {
            // Load "main" targetConfigurations from pipeline config
            def config = loadTargetConfigurations((String)version, variant, "", ignore_platforms)
            if (!config) {
                def error =  "Empty mainTargetConfigurations"
                echo "${error}"
                throw new Exception("${error}")
            }       
            mainTargetConfigurations = JsonOutput.toJson(config)
        }
        if (triggerEvaluationBuild && evaluationTargetConfigurations == "") {
            // Load "evaluation" targetConfigurations from pipeline config
            def config = loadTargetConfigurations((String)version, variant, "_evaluation", ignore_platforms)
            if (!config) {
                def error =  "Empty evaluationTargetConfigurations"
                echo "${error}"
                // Evaluation config can be empty if none for this version
                triggerEvaluationBuild = false
            } else {
                evaluationTargetConfigurations = JsonOutput.toJson(config) 
            }
        }
    }
} // End: node('worker')

if (triggerMainBuild || triggerEvaluationBuild) {
    // Set version suffix, jdk8 has different mechanism to jdk11+
    def additionalConfigureArgs = (version > 8) ? "--with-version-opt=ea" : ""

    // Trigger pipeline builds for main & evaluation of the new build tag and publish with the "ea" tag
    def jobs = [:]
    def pipelines = [:]

    // Trigger Main pipeline as long as we have a non-empty target configuration
    if (triggerMainBuild && mainTargetConfigurations != "{}") {
        pipelines["main"] = "build-scripts/openjdk${version}-pipeline"
        echo "main build targetConfigurations:"
        echo JsonOutput.prettyPrint(mainTargetConfigurations)
    }
    // Trigger Evaluation as long as we have a non-empty target configuration
    if (triggerEvaluationBuild && evaluationTargetConfigurations != "{}") {
        pipelines["evaluation"] = "build-scripts/evaluation-openjdk${version}-pipeline"
        echo "evaluation build targetConfigurations:"
        echo JsonOutput.prettyPrint(evaluationTargetConfigurations)
    }

    pipelines.keySet().each { pipeline_type ->
        def pipeline = pipelines[pipeline_type]
        jobs[pipeline] = {
            catchError {
                stage("Trigger build pipeline - ${pipeline}") {
                    echo "Triggering ${pipeline} for $latestAdoptTag"

                    def jobParams = [
                            string(name: 'releaseType',             value: "Weekly"),
                            string(name: 'scmReference',            value: "$latestAdoptTag"),
                            string(name: 'overridePublishName',     value: "$publishJobTag"),
                            booleanParam(name: 'aqaAutoGen',        value: true),
                            booleanParam(name: 'enableTests',       value: enableTesting),
                            string(name: 'additionalConfigureArgs', value: "$additionalConfigureArgs")
                        ]

                    // Specify the required targetConfigurations
                    if (pipeline_type == "main") {
                        jobParams.add(text(name: 'targetConfigurations',   value: JsonOutput.prettyPrint(mainTargetConfigurations)))
                    }
                    if (pipeline_type == "evaluation") {
                        jobParams.add(text(name: 'targetConfigurations',   value: JsonOutput.prettyPrint(evaluationTargetConfigurations)))
                    }

                    def job = build job: "${pipeline}", propagate: true, parameters: jobParams

                    echo "Triggered ${pipeline} build result = "+ job.getResult()
                }
            }
        }
    }

    parallel jobs
}

