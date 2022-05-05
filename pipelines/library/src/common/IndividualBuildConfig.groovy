package common

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class IndividualBuildConfig implements Serializable {
    final String ARCHITECTURE
    final String TARGET_OS
    final String VARIANT
    final String JAVA_TO_BUILD
    final List<String> TEST_LIST
    final List<String> DYNAMIC_LIST
    final List<String> NUM_MACHINES
    final String SCM_REF
    final String AQA_REF
    final boolean AQA_AUTO_GEN
    final String BUILD_ARGS
    final String NODE_LABEL
    final String ADDITIONAL_TEST_LABEL
    final boolean KEEP_TEST_REPORTDIR
    final String ACTIVE_NODE_TIMEOUT
    final boolean CODEBUILD
    final String DOCKER_IMAGE
    final String DOCKER_FILE
    final String DOCKER_NODE
    final String DOCKER_REGISTRY
    final String DOCKER_CREDENTIAL
    final String PLATFORM_CONFIG_LOCATION
    final String CONFIGURE_ARGS
    final String OVERRIDE_FILE_NAME_VERSION
    final boolean USE_ADOPT_SHELL_SCRIPTS
    final String ADDITIONAL_FILE_NAME_TAG
    final String JDK_BOOT_VERSION
    final boolean RELEASE
    final String PUBLISH_NAME
    final String ADOPT_BUILD_NUMBER
    final boolean ENABLE_TESTS
    final boolean ENABLE_TESTDYNAMICPARALLEL
    final boolean ENABLE_INSTALLERS
    final boolean ENABLE_SIGNER
    final boolean CLEAN_WORKSPACE
    final boolean CLEAN_WORKSPACE_AFTER
    final boolean CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER

    IndividualBuildConfig(String json) {
        this(new JsonSlurper().parseText(json) as Map)
    }

    IndividualBuildConfig(Map<String, ?> map) {
        ARCHITECTURE = trimString(map, "ARCHITECTURE")
        TARGET_OS = trimString(map, "TARGET_OS")
        VARIANT = trimString(map, "VARIANT")
        JAVA_TO_BUILD = trimString(map, "JAVA_TO_BUILD")

        if (String.class.isInstance(map.get("TEST_LIST"))) {
            TEST_LIST = map.get("TEST_LIST").split(",")
        } else if (List.class.isInstance(map.get("TEST_LIST"))) {
            TEST_LIST = map.get("TEST_LIST")
        } else {
            TEST_LIST = []
        }

        if (String.class.isInstance(map.get("DYNAMIC_LIST"))) {
            DYNAMIC_LIST = map.get("DYNAMIC_LIST").split(",")
        } else if (List.class.isInstance(map.get("DYNAMIC_LIST"))) {
            DYNAMIC_LIST = map.get("DYNAMIC_LIST")
        } else {
            DYNAMIC_LIST = []
        }

        if (String.class.isInstance(map.get("NUM_MACHINES"))) {
            NUM_MACHINES = map.get("NUM_MACHINES").split(",")
        } else if (List.class.isInstance(map.get("NUM_MACHINES"))) {
            NUM_MACHINES = map.get("NUM_MACHINES")
        } else {
            NUM_MACHINES = []
        }

        SCM_REF = trimString(map, "SCM_REF")
        AQA_REF = trimString(map, "AQA_REF")
        AQA_AUTO_GEN = map.get("AQA_AUTO_GEN")
        BUILD_ARGS = trimString(map, "BUILD_ARGS")
        NODE_LABEL = trimString(map, "NODE_LABEL")
        ADDITIONAL_TEST_LABEL = trimString(map, "ADDITIONAL_TEST_LABEL")
        KEEP_TEST_REPORTDIR = trimString(map, "KEEP_TEST_REPORTDIR")
        ACTIVE_NODE_TIMEOUT = trimString(map, "ACTIVE_NODE_TIMEOUT")
        CODEBUILD = map.get("CODEBUILD")
        DOCKER_IMAGE = trimString(map, "DOCKER_IMAGE")
        DOCKER_FILE = trimString(map, "DOCKER_FILE")
        DOCKER_NODE = trimString(map, "DOCKER_NODE")
        DOCKER_REGISTRY = trimString(map, "DOCKER_REGISTRY")
        DOCKER_CREDENTIAL = trimString(map, "DOCKER_CREDENTIAL")
        PLATFORM_CONFIG_LOCATION = trimString(map, "PLATFORM_CONFIG_LOCATION")
        CONFIGURE_ARGS = trimString(map, "CONFIGURE_ARGS")
        OVERRIDE_FILE_NAME_VERSION = trimString(map, "OVERRIDE_FILE_NAME_VERSION")
        USE_ADOPT_SHELL_SCRIPTS = map.get("USE_ADOPT_SHELL_SCRIPTS")
        ADDITIONAL_FILE_NAME_TAG = trimString(map, "ADDITIONAL_FILE_NAME_TAG")
        JDK_BOOT_VERSION = trimString(map, "JDK_BOOT_VERSION")
        RELEASE = map.get("RELEASE")
        PUBLISH_NAME = trimString(map, "PUBLISH_NAME")
        ADOPT_BUILD_NUMBER = trimString(map, "ADOPT_BUILD_NUMBER")
        ENABLE_TESTS = map.get("ENABLE_TESTS")
        ENABLE_TESTDYNAMICPARALLEL = map.get("ENABLE_TESTDYNAMICPARALLEL")
        ENABLE_INSTALLERS = map.get("ENABLE_INSTALLERS")
        ENABLE_SIGNER = map.get("ENABLE_SIGNER")
        CLEAN_WORKSPACE = map.get("CLEAN_WORKSPACE")
        CLEAN_WORKSPACE_AFTER = map.get("CLEAN_WORKSPACE_AFTER")
        CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER = map.get("CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER")
    }

    Map<String, ?> toMap() {
        toRawMap().findAll { key, value ->
            value != null
        }
    }

    List<String> toEnvVars() {
        return toRawMap().collect { key, value ->
            if (value == null) {
                value = ""
            }
            return "${key}=${value}"
        }
    }

    Map<String, ?> toRawMap() {
        [
                ARCHITECTURE              : ARCHITECTURE,
                TARGET_OS                 : TARGET_OS,
                VARIANT                   : VARIANT,
                JAVA_TO_BUILD             : JAVA_TO_BUILD,
                TEST_LIST                 : TEST_LIST,
                DYNAMIC_LIST              : DYNAMIC_LIST,
                NUM_MACHINES              : NUM_MACHINES,
                SCM_REF                   : SCM_REF,
                AQA_REF                   : AQA_REF,
                AQA_AUTO_GEN              : AQA_AUTO_GEN,
                BUILD_ARGS                : BUILD_ARGS,
                NODE_LABEL                : NODE_LABEL,
                ADDITIONAL_TEST_LABEL     : ADDITIONAL_TEST_LABEL,
                KEEP_TEST_REPORTDIR       : KEEP_TEST_REPORTDIR,
                ACTIVE_NODE_TIMEOUT       : ACTIVE_NODE_TIMEOUT,
                CODEBUILD                 : CODEBUILD,
                DOCKER_IMAGE              : DOCKER_IMAGE,
                DOCKER_FILE               : DOCKER_FILE,
                DOCKER_NODE               : DOCKER_NODE,
                DOCKER_REGISTRY           : DOCKER_REGISTRY,
                DOCKER_CREDENTIAL         : DOCKER_CREDENTIAL,
                PLATFORM_CONFIG_LOCATION  : PLATFORM_CONFIG_LOCATION,
                CONFIGURE_ARGS            : CONFIGURE_ARGS,
                OVERRIDE_FILE_NAME_VERSION: OVERRIDE_FILE_NAME_VERSION,
                USE_ADOPT_SHELL_SCRIPTS   : USE_ADOPT_SHELL_SCRIPTS,
                ADDITIONAL_FILE_NAME_TAG  : ADDITIONAL_FILE_NAME_TAG,
                JDK_BOOT_VERSION          : JDK_BOOT_VERSION,
                RELEASE                   : RELEASE,
                PUBLISH_NAME              : PUBLISH_NAME,
                ADOPT_BUILD_NUMBER        : ADOPT_BUILD_NUMBER,
                ENABLE_TESTS              : ENABLE_TESTS,
                ENABLE_TESTDYNAMICPARALLEL: ENABLE_TESTDYNAMICPARALLEL,
                ENABLE_INSTALLERS         : ENABLE_INSTALLERS,
                ENABLE_SIGNER             : ENABLE_SIGNER,
                CLEAN_WORKSPACE           : CLEAN_WORKSPACE,
                CLEAN_WORKSPACE_AFTER     : CLEAN_WORKSPACE_AFTER,
                CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER : CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER
        ]
    }

    /**
      * trim whitespace for String input parameter if it is set
      * @param map  build_config map
      * @param key  key of build_config map
      * @return     trimmed value or null if not set
    */
    String trimString(Map map, String key){
        return map.get(key) ? map.get(key).trim() : null
    }

    String toJson() {
        return JsonOutput.prettyPrint(JsonOutput.toJson(toMap()))
    }

    IndividualBuildConfig fromJson(String json) {
        def map = new groovy.json.JsonSlurper().parseText(json) as Map
        return new IndividualBuildConfig(map)
    }

    List<?> toBuildParams() {
        List<?> buildParams = []

        buildParams.add(['$class': 'LabelParameterValue', name: 'NODE_LABEL', label: NODE_LABEL])
        buildParams.add(['$class': 'TextParameterValue', name: 'BUILD_CONFIGURATION', value: toJson()])

        return buildParams
    }
}
