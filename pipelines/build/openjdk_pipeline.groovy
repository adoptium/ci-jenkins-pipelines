import groovy.json.JsonSlurper
import java.nio.file.NoSuchFileException

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

def javaToBuild = "jdk${params.jdkVersion}"
def scmVars = null
Closure configureBuild = null
def buildConfigurations = null
Map<String, ?> DEFAULTS_JSON = null


// Resolve a "-ga" tag to the actual upstream openjdk build tag of the same commit
// Also check adoptium mirror for "dryrun" tags
def resolveGaTag(String jdkVersion, String jdkBranch) {
    def resolvedTag = jdkBranch // Default to as-is
    
    def openjdkRepo = "https://github.com/openjdk/jdk${jdkVersion}.git"
                      
    def gaCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${jdkBranch}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")
    if (gaCommitSHA == "") {
        // Try "updates" repo..
        openjdkRepo = "https://github.com/openjdk/jdk${jdkVersion}u.git"
        gaCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${jdkBranch}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")
    }
    if (gaCommitSHA == "") {
        // Maybe an Adoptium "dryrun" try Adoptium mirror repo..
        openjdkRepo = "https://github.com/adoptium/jdk${jdkVersion}.git"
        gaCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${jdkBranch}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")
    }
    if (gaCommitSHA == "") {
        // Maybe an Adoptium "dryrun" try Adoptium mirror "updates" repo..
        openjdkRepo = "https://github.com/adoptium/jdk${jdkVersion}u.git"
        gaCommitSHA = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${jdkBranch}\" | tr -s '\\t ' ' ' | cut -d' ' -f1 | tr -d '\\n'")
    }

    if (gaCommitSHA == "") {
        println "[ERROR] Unable to resolve ${jdkBranch} upstream commit, will try to match tag as-is"
    } else {
        def upstreamTag = sh(returnStdout: true, script:"git ls-remote --tags ${openjdkRepo} | grep '\\^{}' | grep \"${gaCommitSHA}\" | grep -v \"${jdkBranch}\" | tr -s '\\t ' ' ' | cut -d' ' -f2 | sed \"s,refs/tags/,,\" | sed \"s,\\^{},,\" | tr -d '\\n'")
        if (upstreamTag != "") {
            println "[INFO] Resolved ${jdkBranch} to upstream build tag ${upstreamTag}"
            resolvedTag = upstreamTag
        } else {
            println "[ERROR] Unable to resolve ${jdkBranch} upstream commit, will try to match tag as-is"
        }
    }

    return resolvedTag
}

node('worker') {
    // Ensure workspace is clean so we don't archive any old failed pipeline artifacts
    println '[INFO] Cleaning up controller worker workspace prior to running pipelines..'
    // Fail if unable to clean..
    cleanWs notFailBuild: false

    if (params.releaseType == 'Release' && params.aqaReference != '' && params.scmReference != '') {
        def propertyFile = 'testenv.properties'
        if (params.jdkVersion == '8' && params.targetConfigurations.contains('arm32Linux')) {
            propertyFile = 'testenv_arm32.properties'
        }
        if ( ! ( "${params.aqareference}" ==~ /^[A-Za-z0-9\/\.\-_]*$/ ) ) {
          throw new Exception("[ERROR] Dubious characters in aqa reference - aborting");
        }
        sh("curl -Os https://raw.githubusercontent.com/adoptium/aqa-tests/${params.aqaReference}/testenv/${propertyFile}")

        def buildTag = params.scmReference
        if (params.scmReference.contains('_adopt')) {
            buildTag = params.scmReference.substring(0, params.scmReference.length() - 6) // remove _adopt suffix
        }

        def list = readFile("${propertyFile}").readLines()
        def jdkBranch = ""
        def jdkOpenj9Branch = ""
        for (item in list) {
            if (item.contains("JDK${params.jdkVersion}_BRANCH")) {
                def branchInfo = item.split('=')
                jdkBranch = branchInfo[1]
            } else if (item.contains("JDK${params.jdkVersion}_OPENJ9_BRANCH")) {
                def branchInfo = item.split('=')
                jdkOpenj9Branch = branchInfo[1]
            }
            if (jdkBranch && jdkOpenj9Branch) {
                break
            }
        }

        // If testenv tag is a "-ga" tag, then resolve to the actual openjdk build tag it's tagging
        if (jdkBranch.contains("-ga")) {
            jdkBranch = resolveGaTag("${params.jdkVersion}", jdkBranch)
        }

        if (jdkBranch == buildTag) {
            println "[INFO] scmReference=${buildTag} matches with JDK${params.jdkVersion}_BRANCH=${jdkBranch} in ${propertyFile} in aqa-tests release branch."
        } else if (jdkOpenj9Branch == buildTag) {
            println "[INFO] scmReference=${buildTag} matches with JDK${params.jdkVersion}_OPENJ9_BRANCH=${jdkOpenj9Branch} in ${propertyFile} in aqa-tests release branch."
        } else {
            println "[ERROR] scmReference does not match with any JDK branch in ${propertyFile} in aqa-tests release branch. Please update aqa-tests ${params.aqaReference} release branch. Set the current build result to FAILURE!"
            currentBuild.result = 'FAILURE'
            return
        }
    }
    // Load defaultsJson. These are passed down from the build_pipeline_generator and is a JSON object containing user's default constants.
    if (!params.defaultsJson || defaultsJson == '') {
        throw new Exception('[ERROR] No User Defaults JSON found! Please ensure the defaultsJson parameter is populated and not altered during parameter declaration.')
    } else {
        DEFAULTS_JSON = new JsonSlurper().parseText(defaultsJson) as Map
    }

    // Load adoptDefaultsJson. These are passed down from the build_pipeline_generator and is a JSON object containing adopt's default constants.
    if (!params.adoptDefaultsJson || adoptDefaultsJson == '') {
        throw new Exception('[ERROR] No Adopt Defaults JSON found! Please ensure the adoptDefaultsJson parameter is populated and not altered during parameter declaration.')
    } else {
        ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(adoptDefaultsJson) as Map
    }

    /*
    Changes dir to Adopt's pipeline repo. Use closures as functions aren't accepted inside node blocks
    */
    def checkoutAdoptPipelines = { ->
        checkout([$class: 'GitSCM',
        branches: [ [ name: ADOPT_DEFAULTS_JSON['repository']['pipeline_branch'] ] ],
        userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON['repository']['pipeline_url'] ] ]
      ])
    }

    scmVars = checkout scm

    String helperRef = DEFAULTS_JSON['repository']['helper_ref']
    library(identifier: "openjdk-jenkins-helper@${helperRef}")

    // Load baseFilePath. This is where build_base_file.groovy is located. It runs the downstream job setup and configuration retrieval services.
    def baseFilePath = (params.baseFilePath) ?: DEFAULTS_JSON['baseFileDirectories']['upstream']
    try {
        configureBuild = load "${WORKSPACE}/${baseFilePath}"
    } catch (NoSuchFileException e) {
        println "[WARNING] ${baseFilePath} does not exist in your repository. Attempting to pull Adopt's base file script instead."

        checkoutAdoptPipelines()
        configureBuild = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['baseFileDirectories']['upstream']}"
        checkout scm
    }

    // Load buildConfigFilePath. This is where jdkxx_pipeline_config.groovy is located. It contains the build configurations for each platform, architecture and variant.
    def buildConfigFilePath = (params.buildConfigFilePath) ?: "${DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy"

    // Check if pipeline is jdk11 or jdk11u
    def configPath =  "${WORKSPACE}/${buildConfigFilePath}"
    if (fileExists(configPath)) {
        println "Found ${buildConfigFilePath}"
    } else {
        javaToBuild = "${javaToBuild}u"
        buildConfigFilePath = (params.buildConfigFilePath) ?: "${DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy"
    }

    try {
        buildConfigurations = load "${WORKSPACE}/${buildConfigFilePath}"
    } catch (NoSuchFileException e) {
        println "[WARNING] ${buildConfigFilePath} does not exist in your repository. Attempting to pull Adopt's build configs instead."

        checkoutAdoptPipelines()

        // Reset javaToBuild to original value before trying again. Converts 11u to 11
        javaToBuild = javaToBuild.replaceAll('u', '')

        // Check if pipeline is jdk11 or jdk11u
        configPath =  "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy"
        if (fileExists(configPath)) {
            buildConfigurations = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy"
        } else {
            javaToBuild = "${javaToBuild}u"
            buildConfigurations = load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy"
        }
        checkout scm
    }
}

// If a parameter below hasn't been declared above, it is declared in the jenkins job itself
if (scmVars != null || configureBuild != null || buildConfigurations != null) {
    try {
        configureBuild(
            javaToBuild,
            buildConfigurations,
            targetConfigurations,
            DEFAULTS_JSON,
            activeNodeTimeout,
            dockerExcludes,
            enableReproducibleCompare,
            enableTests,
            enableTestDynamicParallel,
            enableInstallers,
            enableSigner,
            releaseType,
            scmReference,
            buildReference,
            ciReference,
            helperReference,
            aqaReference,
            aqaAutoGen,
            overridePublishName,
            useAdoptBashScripts,
            additionalConfigureArgs,
            scmVars,
            additionalBuildArgs,
            overrideFileNameVersion,
            cleanWorkspaceBeforeBuild,
            cleanWorkspaceAfterBuild,
            cleanWorkspaceBuildOutputAfterBuild,
            adoptBuildNumber,
            propagateFailures,
            keepTestReportDir,
            keepReleaseLogs,
            currentBuild,
            this,
            env
        ).doBuild()
    } finally {
        node('worker') {
            println '[INFO] Cleaning up controller worker workspace...'
            cleanWs notFailBuild: true
        }
    }
} else {
    throw new Exception("[ERROR] One or more setup parameters are null.\nscmVars = ${scmVars}\nconfigureBuild = ${configureBuild}\nbuildConfigurations = ${buildConfigurations}")
}
