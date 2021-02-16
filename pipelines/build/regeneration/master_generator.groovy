import groovy.json.JsonOutput

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

node('master') {
  List RETIRED_VERSIONS = [9, 10, 12, 13, 14, 15]

  // Pull in Adopt defaults
  String ADOPT_DEFAULTS_FILE_URL = "https://raw.githubusercontent.com/AdoptOpenJDK/ci-jenkins-pipelines/master/pipelines/defaults.json"
  def getAdopt = new URL(ADOPT_DEFAULTS_FILE_URL).openConnection()
  Map<String, ?> ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
  if (!ADOPT_DEFAULTS_JSON || !Map.class.isInstance(ADOPT_DEFAULTS_JSON)) {
    throw new Exception("[ERROR] No ADOPT_DEFAULTS_JSON found at ${ADOPT_DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file. NOTE: Since this adopt's defaults and unlikely to change location, this is likely a network or GitHub issue.")
  }

  // Pull in User defaults
  String DEFAULTS_FILE_URL = (params.DEFAULTS_URL) ?: ADOPT_DEFAULTS_FILE_URL
  def getUser = new URL(DEFAULTS_FILE_URL).openConnection()
  Map<String, ?> DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
  if (!DEFAULTS_JSON || !Map.class.isInstance(DEFAULTS_JSON)) {
    throw new Exception("[ERROR] No DEFAULTS_JSON found at ${DEFAULTS_FILE_URL} or it is not a valid JSON object. Please ensure this path is correct and leads to a JSON or Map object file.")
  }

  Map remoteConfigs = [:]
  def repoBranch = null

  /*
  Changes dir to Adopt's repo. Use closures as functions aren't accepted inside node blocks
  */
  def checkoutAdopt = { ->
    checkout([$class: 'GitSCM',
      branches: [ [ name: ADOPT_DEFAULTS_JSON["repositories"]["branch"] ] ],
      userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repositories"]["pipeline_url"] ] ]
    ])
  }

  /*
  Changes dir to the user's repo. Use closures as functions aren't accepted inside node blocks
  */
  def checkoutUser = { ->
    checkout([$class: 'GitSCM',
      branches: [ [ name: repoBranch ] ],
      userRemoteConfigs: [ remoteConfigs ]
    ])
  }

  /*
  * Create the upstream job, using adopt's template if the user's one fails
  */
  def generateJob = { generatorType, config ->
    /*
    COMMON PARAMS BETWEEN BOTH GENERATOR JOBS
    */

    // Load scriptPath. This is where the generator script is located compared to repository root.
    // TODO: THIS IS BROKEN
    String scriptPath = (params."${generatorType.toUpperCase()}_GENERATOR_SCRIPT_PATH") ?: DEFAULTS_JSON['scriptDirectories'][generatorType]
    if (!fileExists(scriptPath)) {
      println "[WARNING] ${scriptPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
      checkoutAdopt()
      scriptPath = ADOPT_DEFAULTS_JSON['scriptDirectories'][generatorType]
      println "[SUCCESS] The path is now ${scriptPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
      checkoutUser()
    }
    config.SCRIPT = scriptPath

    // Load jobTemplatePath. This is where the job template (downstream or upstream) is located compared to repository root.
    String jobTemplatePath = (params."${generatorType.toUpperCase()}_JOB_TEMPLATE_PATH") ?: DEFAULTS_JSON['templateDirectories'][generatorType]
    if (!fileExists(jobTemplatePath)) {
      println "[WARNING] ${jobTemplatePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
      checkoutAdopt()
      jobTemplatePath = ADOPT_DEFAULTS_JSON['templateDirectories'][generatorType]
      println "[SUCCESS] The path is now ${jobTemplatePath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
      checkoutUser()
    }
    config["${generatorType.toUpperCase()}_JOB_TEMPLATE"] = jobTemplatePath

    // Load baseFilePath. The location of the downstream base file (called from the initial script) compared to the repository root.
    String baseFilePath = (params."${generatorType.toUpperCase()}_BASEFILE_PATH") ?: DEFAULTS_JSON['baseFileDirectories'][generatorType]
    if (!fileExists(baseFilePath)) {
      println "[WARNING] ${baseFilePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
      checkoutAdopt()
      baseFilePath = ADOPT_DEFAULTS_JSON['baseFileDirectories'][generatorType]
      println "[SUCCESS] The path is now ${baseFilePath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
      checkoutUser()
    }
    config.BASE_FILE = baseFilePath

    /*
    * UPSTREAM SPECIFIC PARAMS
    */
    if (generatorType == "upstream") {
      // Load enablePipelineSchedule. This determines whether we will be generating the pipelines with a schedule (defined in jdkxx.groovy) or not.
      Boolean enablePipelineSchedule = false
      if (params.ENABLE_PIPELINE_SCHEDULE) {
        enablePipelineSchedule = true
      }
      config.ENABLE_PIPELINE_SCHEDULE = enablePipelineSchedule

      // Load useAdoptShellScripts. This determines whether we will checkout to adopt's repository before running make-adopt-build-farm.sh or if we use the user's bash scripts.
      Boolean useAdoptShellScripts = false
      if (params.USE_ADOPT_SHELL_SCRIPTS) {
        useAdoptShellScripts = true
      }
      config.USE_ADOPT_SHELL_SCRIPTS = useAdoptShellScripts

      // Load scriptFolderPath. This is the folder where the openjdk_pipeline.groovy code is located compared to the repository root. These are the top level pipeline jobs.
      String scriptFolderPath = (params.SCRIPT_FOLDER_PATH) ?: DEFAULTS_JSON["scriptDirectories"]["upstream"]
      if (!fileExists(scriptFolderPath)) {
        println "[WARNING] ${scriptFolderPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
        checkoutAdopt()
        scriptFolderPath = ADOPT_DEFAULTS_JSON['scriptDirectories']['upstream']
        println "[SUCCESS] The path is now ${scriptFolderPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
        checkoutUser()
      }
      config.SCRIPT_FOLDER_PATH = scriptFolderPath

      // Load weeklyTemplatePath. This is where the weekly job template is located compared to repository root.
      String weeklyTemplatePath = (params.WEEKLY_JOB_TEMPLATE_PATH) ?: DEFAULTS_JSON['templateDirectories']['weekly']
      if (!fileExists(weeklyTemplatePath)) {
        println "[WARNING] ${weeklyTemplatePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
        checkoutAdopt()
        weeklyTemplatePath = ADOPT_DEFAULTS_JSON['templateDirectories']['weekly']
        println "[SUCCESS] The path is now ${weeklyTemplatePath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
        checkoutUser()
      }
      config.WEEKLY_JOB_TEMPLATE = weeklyTemplatePath

    /*
    * DOWNSTREAM SPECIFIC PARAMS
    */
    } else {
      // Load sleepTime. This determines how long the downstream generator will sleep for before checking again if a job is running.
      Integer sleepTime = 900
      if (params.SLEEP_TIME) {
        sleepTime = Integer.parseInt(DOWNSTREAM_SLEEP_TIME)
      }
      config.SLEEP_TIME = sleepTime

      // Load excludesList. This will contain any platform/variant keys the user will wish to exclude from generating (even if they are in the nightly config files)
      Map excludesList = [:]
      if (params.DOWNSTREAM_EXCLUDES_LIST) {
        excludesList = new JsonSlurper().parseText(DOWNSTREAM_EXCLUDES_LIST) as Map
      }
      config.EXCLUDES_LIST = excludesList

      // Load downstreamScriptPath. The location of the initial downstream script file compared to the repository root.
      String downstreamScriptPath = (params.DOWNSTREAM_SCRIPT_PATH) ?: DEFAULTS_JSON['scriptDirectories']['downstream']
      if (!fileExists(downstreamScriptPath)) {
        println "[WARNING] ${downstreamScriptPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
        checkoutAdopt()
        downstreamScriptPath = ADOPT_DEFAULTS_JSON['scriptDirectories']['downstream']
        println "[SUCCESS] The path is now ${downstreamScriptPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
        checkoutUser()
      }
      config.SCRIPT_PATH = downstreamScriptPath

      // Load regenBaseFilePath. The location of the downstream regeneration file (called from the initial script) compared to the repository root.
      String regenBaseFilePath = (params.REGEN_BASEFILE_PATH) ?: DEFAULTS_JSON['scriptDirectories']['generators']['downstreamBase']
      if (!fileExists(regenBaseFilePath)) {
        println "[WARNING] ${regenBaseFilePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
        checkoutAdopt()
        regenBaseFilePath = ADOPT_DEFAULTS_JSON['scriptDirectories']['generators']['downstreamBase']
        println "[SUCCESS] The path is now ${regenBaseFilePath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
        checkoutUser()
      }
      config.REGEN_FILE = regenBaseFilePath

      // Load credentials to be used in commuinicating with the jenkins api. This is in case the jenkins server is private. It's pulled from the template to avoid printout (hence the excessive groovylint rule ignores)
      /* groovylint-disable-next-line NoDef, UnusedVariable, VariableTypeRequired */
      def jenkinsCreds = ""
      if (params.JENKINS_AUTH) {
        jenkinsCreds = JENKINS_AUTH
      } else {
        println "[WARNING] No Jenkins API Credentials have been provided! If your server does not have anonymous read enabled, you may encounter 403 api request error codes."
      }
    }

    // Load generatorTemplatePath. This is where the generator template is located compared to repository root.
    String generatorTemplatePath = (params."${generatorType.toUpperCase()}_GENERATOR_TEMPLATE_PATH") ?: DEFAULTS_JSON['templateDirectories']['generators'][generatorType]
    if (!fileExists(generatorTemplatePath)) {
      println "[WARNING] ${generatorTemplatePath} does not exist in your chosen repository. Updating it to use Adopt's instead"
      checkoutAdopt()
      generatorTemplatePath = ADOPT_DEFAULTS_JSON['templateDirectories']['generators'][generatorType]
      println "[SUCCESS] The path is now ${generatorTemplatePath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
      checkoutUser()
    }
    config["${generatorType.toUpperCase()}_GENERATOR_TEMPLATE"] = generatorTemplatePath

    println "[INFO] FINAL CONFIG FOR ${generatorType.toUpperCase()} ${config.JOB_NAME}"
    println JsonOutput.prettyPrint(JsonOutput.toJson(config))

    // Create job, using adopt's template if necessary
    try {
      jobDsl targets: config["${generatorType.toUpperCase()}_GENERATOR_TEMPLATE"], ignoreExisting: false, additionalParameters: config
    } catch (Exception e) {
      println "${e}\n[WARNING] Something went wrong when creating the job dsl. It may be because we are trying to pull the template inside a user repository. Using Adopt's template instead..."
      checkoutAdopt()
      jobDsl targets: ADOPT_DEFAULTS_JSON['templateDirectories']['generators'][generatorType], ignoreExisting: false, additionalParameters: config
      checkoutUser()
    }
  }

  /*****
  * START *
  *****/
  def repoUri = (params.REPOSITORY_URL) ?: DEFAULTS_JSON["repositories"]["pipeline_url"]
  repoBranch = (params.REPOSITORY_BRANCH) ?: DEFAULTS_JSON["repositories"]["branch"]

  // Load credentials to be used in checking out. This is in case we are checking out a URL that is not Adopts and they don't have their ssh key on the machine.
  def checkoutCreds = (params.CHECKOUT_CREDENTIALS) ?: ""
  remoteConfigs = [ url: repoUri ]
  if (checkoutCreds != "") {
    // NOTE: This currently does not work with user credentials due to https://issues.jenkins.io/browse/JENKINS-60349
    remoteConfigs.put("credentials", "${checkoutCreds}")
  } else {
    println "[WARNING] CHECKOUT_CREDENTIALS not specified! Checkout to $repoUri may fail if you do not have your ssh key on this machine."
  }

  // Checkout into user repository
  checkoutUser()

  // Load jobRoot. This is where the generator job will be created at.
  def genJobRoot = (params.GENERATOR_JOB_ROOT) ?: DEFAULTS_JSON["jenkinsDetails"]["generatorDirectory"]

  // Load jobRoot. This is where the pipeline and downstream jobs will be created at.
  def buildJobRoot = (params.BUILD_JOB_ROOT) ?: DEFAULTS_JSON["jenkinsDetails"]["rootDirectory"]

  // Load nightlyFolderPath. This is where the pipeline scheduling details and nightly platforms are located.
  String nightlyFolderPath = (params.NIGHTLY_FOLDER_PATH) ?: DEFAULTS_JSON['configDirectories']['nightly']
  if (!fileExists(nightlyFolderPath)) {
    println "[WARNING] ${nightlyFolderPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
    checkoutAdopt()
    nightlyFolderPath = ADOPT_DEFAULTS_JSON['configDirectories']['nightly']
    println "[SUCCESS] The path is now ${nightlyFolderPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
    checkoutUser()
  }

  // Load buildFolderPath. This is where the platform and variant config details are located.
  String buildFolderPath = (params.BUILD_FOLDER_PATH) ?: DEFAULTS_JSON['configDirectories']['build']
  if (!fileExists(buildFolderPath)) {
    println "[WARNING] ${buildFolderPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
    checkoutAdopt()
    buildFolderPath = ADOPT_DEFAULTS_JSON['configDirectories']['build']
    println "[SUCCESS] The path is now ${buildFolderPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
    checkoutUser()
  }

  // Load libraryPath. This imports the adopt class library containing groovy classes used for carrying across metadata between jobs.
  String libraryPath = (params.LIBRARY_PATH) ?: DEFAULTS_JSON['importLibraryScript']
  if (!fileExists(libraryPath)) {
    println "[WARNING] ${libraryPath} does not exist in your chosen repository. Updating it to use Adopt's instead"
    checkoutAdopt()
    libraryPath = ADOPT_DEFAULTS_JSON['importLibraryScript']
    println "[SUCCESS] The path is now ${libraryPath} relative to ${ADOPT_DEFAULTS_JSON['repositories']['pipeline_url']}"
    checkoutUser()
  }

  // Load jenkinsBuildRoot. This is the root directory of the user's jenkins server.
  String jenkinsBuildRoot = (params.JENKINS_BUILD_ROOT) ?: DEFAULTS_JSON['jenkinsDetails']['rootUrl']

  // Initialise config
  Map config = [
    GIT_URL             : repoUri,
    GIT_BRANCH          : repoBranch,
    GENERATION_FOLDER   : genJobRoot,
    BUILD_FOLDER        : buildJobRoot,
    JENKINS_BUILD_ROOT  : jenkinsBuildRoot,
    JOB_NAME            : "build-pipeline-generator",
    JAVA_VERSION        : "",
    CHECKOUT_CREDENTIALS: checkoutCreds,
    RETIRED_VERSIONS    : RETIRED_VERSIONS,
    NIGHTLY_FOLDER_PATH : nightlyFolderPath,
    BUILD_FOLDER_PATH   : buildFolderPath,
    LIBRARY_PATH        : libraryPath,
    DEFAULTS_JSON       : DEFAULTS_JSON,
    ADOPT_DEFAULTS_JSON : ADOPT_DEFAULTS_JSON
  ]

  generateJob("upstream", config)

  // Collect available JDK versions to check for generation (tip_version + 1 just in case it is out of date on a release day)
  def JobHelper = library(identifier: 'openjdk-jenkins-helper@master').JobHelper
  println "Querying Adopt Api for the JDK-Head number (tip_version)..."
  def response = JobHelper.getAvailableReleases(this)
  int headVersion = (int) response.getAt("tip_version")

  (8..headVersion+1).each({ javaVersion ->
    if (RETIRED_VERSIONS.contains(javaVersion)) {
      println "[INFO] $javaVersion is a retired version that isn't currently built. Skipping generation..."
      return
    }

    config.JAVA_VERSION = javaVersion
    config.JOB_NAME = "pipeline_jobs_generator_jdk${javaVersion}"

    generateJob("downstream", config)
  })
}
