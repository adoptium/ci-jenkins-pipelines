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

node ("worker") {
    // Load defaultsJson. These are passed down from the build_pipeline_generator and is a JSON object containing user's default constants.
    if (!params.defaultsJson || defaultsJson == "") {
        throw new Exception("[ERROR] No User Defaults JSON found! Please ensure the defaultsJson parameter is populated and not altered during parameter declaration.")
    } else {
        DEFAULTS_JSON = new JsonSlurper().parseText(defaultsJson) as Map
    }

    // Load adoptDefaultsJson. These are passed down from the build_pipeline_generator and is a JSON object containing adopt's default constants.
    if (!params.adoptDefaultsJson || adoptDefaultsJson == "") {
        throw new Exception("[ERROR] No Adopt Defaults JSON found! Please ensure the adoptDefaultsJson parameter is populated and not altered during parameter declaration.")
    } else {
        ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(adoptDefaultsJson) as Map
    }

    /*
    Changes dir to Adopt's pipeline repo. Use closures as functions aren't accepted inside node blocks
    */
    def checkoutAdoptPipelines = { ->
      checkout([$class: 'GitSCM',
        branches: [ [ name: ADOPT_DEFAULTS_JSON["repository"]["pipeline_branch"] ] ],
        userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repository"]["pipeline_url"] ] ]
      ])
    }

    scmVars = checkout scm

    // Load the class library so we can use their classes here. If we don't find an import library script in the user's repo, we checkout to temurin-build and use the one that's present there. Finally, we check back out to the user repo.
    def libraryPath = (params.baseFilePath) ?: DEFAULTS_JSON['importLibraryScript']
    try {
        load "${WORKSPACE}/${libraryPath}"
    } catch (Exception e) {
        println "[WARNING] ${libraryPath} could not be loaded, likely because it does not exist in your repository. Error:\n${e}\nAttempting to pull Adopt's library script instead..."

        checkoutAdoptPipelines()
        load "${WORKSPACE}/${ADOPT_DEFAULTS_JSON['importLibraryScript']}"
        checkout scm
    }

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
    def configPath =  new File("${WORKSPACE}/${buildConfigFilePath}")
    if (configPath.exists()) {
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
        javaToBuild = javaToBuild.replaceAll("u", "")

        // Check if pipeline is jdk11 or jdk11u
        configPath =  new File("${WORKSPACE}/${ADOPT_DEFAULTS_JSON['configDirectories']['build']}/${javaToBuild}_pipeline_config.groovy")
        if (configPath.exists()) {
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
    configureBuild(
        javaToBuild,
        buildConfigurations,
        targetConfigurations,
        DEFAULTS_JSON,
        activeNodeTimeout,
        dockerExcludes,
        enableTests,
        enableTestDynamicParallel,
        enableInstallers,
        enableSigner,
        releaseType,
        scmReference,
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
} else {
    throw new Exception("[ERROR] One or more setup parameters are null.\nscmVars = ${scmVars}\nconfigureBuild = ${configureBuild}\nbuildConfigurations = ${buildConfigurations}")
}
