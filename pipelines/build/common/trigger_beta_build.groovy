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

/*
  Detect new upstream OpenJDK source build tag, and trigger a "beta" pipeline build
  if the given build has not already been published.

  The "Force" option can be used to re-build and re-publish the existing latest build.
*/

def variant="${params.VARIANT}"
def mirrorRepo="${params.MIRROR_REPO}"
def version="${params.JDK_VERSION}".toInteger()
def binariesRepo="${params.BINARIES_REPO}"

// GitHub issue configuration for release status checking
def releaseStatusGithubRepo = "adoptium/temurin"
def releaseStatusSearchPhrase = "Release Status per Platform"

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

// Check if a GitHub issue containing the configured search phrase is open
// and was created by an authorized user from release-managers.json
// Returns true if such an issue is open (release ongoing), false otherwise
// NOTE: On any error or failure, assumes release IS ongoing (fail-safe to disable tests)
def isReleaseOngoing(String githubRepo, String searchPhrase) {
    def releaseOngoing = false

    try {
        echo "Checking GitHub ${githubRepo} for open issue containing: '${searchPhrase}'"

        // First, fetch the list of authorized users from release-managers.json
        def authUsersUrl = "https://raw.githubusercontent.com/adoptium/temurin/main/.github/workflows/release-managers.json"
        def rcAuth = sh(script: "curl -s -o release-managers.json '${authUsersUrl}'", returnStatus: true)

        if (rcAuth != 0) {
            echo "ERROR: Failed to fetch authorized users list. Assuming release IS ongoing (fail-safe)."
            return true
        }

        // Extract the authorized_users array from the JSON using jq
        def authorizedUsers = sh(script: "jq -r '.authorized_users[]' release-managers.json | tr '\\n' ' '", returnStdout: true).trim()

        if (authorizedUsers == "") {
            echo "ERROR: No authorized users found in release-managers.json. Assuming release IS ongoing (fail-safe)."
            return true
        }

        echo "Authorized users: ${authorizedUsers}"

        // Use GitHub API to search for issues containing the phrase in the title
        def encodedQuery = URLEncoder.encode("repo:${githubRepo} is:open is:issue in:title \"${searchPhrase}\"", "UTF-8")
        def searchUrl = "https://api.github.com/search/issues?q=${encodedQuery}"

        // Fetch the search results
        def rc = sh(script: "curl -s -o issue_search.json '${searchUrl}'", returnStatus: true)

        if (rc == 0) {
            // Parse the JSON response to check if any issues were found using jq
            def issueCount = sh(script: "jq -r '.total_count' issue_search.json", returnStdout: true).trim()
echo "count: "+issueCount
            if (issueCount.isInteger() && issueCount.toInteger() > 0) {
                echo "Found ${issueCount} open issue(s) containing '${searchPhrase}' in ${githubRepo}"

                // Check all matching issues to see if any were created by an authorized user
                // Extract all issue creators using jq
                def allCreators = sh(script: "jq -r '.items[].user.login' issue_search.json", returnStdout: true).trim()

                if (allCreators != "") {
                    def creators = allCreators.split('\n')
                    echo "Checking ${creators.size()} issue(s) for authorized creators..."

                    for (int i = 0; i < creators.size(); i++) {
                        def issueCreator = creators[i].trim()
                        def issueTitle = sh(script: "jq -r '.items[${i}].title' issue_search.json", returnStdout: true).trim()

                        echo "Issue ${i + 1}: '${issueTitle}' created by '${issueCreator}'"

                        // Check if the creator is in the authorized users list
                        def isAuthorized = sh(script: "echo '${authorizedUsers}' | grep -w '${issueCreator}'", returnStatus: true)

                        if (isAuthorized == 0) {
                            echo "Issue creator '${issueCreator}' is authorized. Release is ongoing."
                            releaseOngoing = true
                            break  // Found an authorized issue, no need to check further
                        } else {
                            echo "Issue creator '${issueCreator}' is NOT in the authorized users list."
                        }
                    }

                    if (!releaseOngoing) {
                        echo "None of the matching issues were created by authorized users. Release is NOT ongoing."
                    }
                } else {
                    echo "ERROR: Could not determine issue creators. Assuming release IS ongoing (fail-safe)."
                    return true
                }
            } else {
                echo "No open issues found containing '${searchPhrase}' in ${githubRepo}"
            }
        } else {
            echo "ERROR: Failed to query GitHub API for issue status. Assuming release IS ongoing (fail-safe)."
            return true
        }
    } catch (Exception e) {
        echo "ERROR: Exception checking GitHub issue status: ${e.message}"
        echo "Assuming release IS ongoing due to error (fail-safe)."
        return true
    }

    echo "Is release ongoing (based on GitHub issue and authorized user)? ${releaseOngoing}"
    return releaseOngoing
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

    // Check if release is ongoing by querying for GitHub issue
    if (isReleaseOngoing(releaseStatusGithubRepo, releaseStatusSearchPhrase)) {
        echo "Release is ongoing (GitHub issue containing '${releaseStatusSearchPhrase}' is open in ${releaseStatusGithubRepo}), so testing is disabled."
        enableTesting = false
    }
exit

    if (!params.FORCE_MAIN && !params.FORCE_EVALUATION) {
        // Determine this versions potential GA tag, so as to not build and publish a GA version
        def versionStr
        if (version > 8) {
            versionStr = latestAdoptTag.substring(0, latestAdoptTag.indexOf("+"))
        } else {
            versionStr = latestAdoptTag.substring(0, latestAdoptTag.indexOf("-"))
        }
   
        // Check binaries repo for existance of the given release tag having being already built?
        def jdkAssetToCheck = "x64_linux"
        if (mirrorRepo.contains("aarch32-jdk8u")) {
            // aarch32-jdk8u built in its own pipeline
            jdkAssetToCheck = "arm_linux"
        } else if (mirrorRepo.contains("alpine-jdk8u")) {
            // alpine-jdk8u built in its own pipeline
            jdkAssetToCheck = "x64_alpine-linux"
        } else if (version == 8 && mainTargetConfigurations.contains("x64Solaris")) {
            // Solaris built in own pipeline
            jdkAssetToCheck = "x64_solaris"
        } else if (version == 8 && mainTargetConfigurations.contains("sparcv9Solaris")) {
            // Solaris built in own pipeline
            jdkAssetToCheck = "sparcv9_solaris"
        }

        echo "Checking if ${binariesRepoTag} is already published for JDK asset ${jdkAssetToCheck} ?"
        def assetExists = checkJDKAssetExistsForArch(binariesRepo, versionStr, binariesRepoTag, jdkAssetToCheck)

        if (assetExists) {
            echo "Build tag ${binariesRepoTag} is already published - nothing to do"
        } else {
            echo "New unpublished build tag ${binariesRepoTag} - triggering builds"
            triggerMainBuild = true
            triggerEvaluationBuild = true
        }
    } else {
        echo "FORCE triggering specified builds.."
        triggerMainBuild = params.FORCE_MAIN
        triggerEvaluationBuild = params.FORCE_EVALUATION
        if (params.BUILD_HEAD) {
            echo "FORCE building HEAD rather than latest tag"
            latestAdoptTag = ""
            publishJobTag = ""
        } 
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
    def solarisBuildJob = false

    // Trigger Main pipeline as long as we have a non-empty target configuration
    if (triggerMainBuild && mainTargetConfigurations != "{}") {
        if (version == 8 && (mainTargetConfigurations.contains("x64Solaris") || mainTargetConfigurations.contains("sparcv9Solaris"))) {
            // Special case to handle building jdk8u Solaris
            if (mainTargetConfigurations.contains("x64Solaris")) {
                pipelines["main"] = "build-scripts/jobs/jdk8u/jdk8u-solaris-x64-temurin-simplepipe"
            } else {
                pipelines["main"] = "build-scripts/jobs/jdk8u/jdk8u-solaris-sparcv9-temurin-simplepipe"
            }
            solarisBuildJob = true
        } else {
            pipelines["main"] = "build-scripts/openjdk${version}-pipeline"
        }
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

                    def jobParams
                    if (solarisBuildJob) {
                        def dryRun
                        if (params.PUBLISH) {
                            dryRun = false
                        } else {
                            dryRun = true
                        }
                        jobParams = [
                            booleanParam(name: 'RELEASE',           value: false),
                            string(name: 'SCM_REF',                 value: "$latestAdoptTag"),
                            booleanParam(name: 'ENABLE_TESTS',      value: enableTesting),
                            booleanParam(name: 'DRY_RUN',           value: dryRun)
                        ]
                    } else {
                        def releaseType
                        if (params.PUBLISH) {
                            releaseType = "Weekly"
                        } else {
                            releaseType = "Weekly Without Publish"
                        }
                        jobParams = [
                            string(name: 'releaseType',             value: releaseType),
                            string(name: 'scmReference',            value: "$latestAdoptTag"),
                            string(name: 'overridePublishName',     value: "$publishJobTag"),
                            booleanParam(name: 'aqaAutoGen',        value: true),
                            booleanParam(name: 'enableTests',       value: enableTesting),
                            string(name: 'additionalConfigureArgs', value: "$additionalConfigureArgs")
                        ]
                    }

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

