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
    final boolean ENABLE_SBOM
    final boolean CLEAN_WORKSPACE
    final boolean CLEAN_WORKSPACE_AFTER
    final boolean CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER

    IndividualBuildConfig(String json) {
        this(new JsonSlurper().parseText(json) as Map)
    }

    IndividualBuildConfig(Map<String, ?> map) {
        ARCHITECTURE = map.get("ARCHITECTURE") != null ? map.get("ARCHITECTURE").trim() : null
        TARGET_OS = map.get("TARGET_OS") != null ? map.get("TARGET_OS").trim() : null
        VARIANT = map.get("VARIANT") != null ? map.get("VARIANT").trim() : null
        JAVA_TO_BUILD = map.get("JAVA_TO_BUILD")!= null ? map.get("JAVA_TO_BUILD").trim() : null

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

        SCM_REF = map.get("SCM_REF") != null ? map.get("SCM_REF").trim() : null
        AQA_REF = map.get("AQA_REF") != null ? map.get("AQA_REF").trim() : null
        AQA_AUTO_GEN = map.get("AQA_AUTO_GEN")
        BUILD_ARGS = map.get("BUILD_ARGS") != null ? map.get("BUILD_ARGS").trim() : null
        NODE_LABEL = map.get("NODE_LABEL") != null ? map.get("NODE_LABEL").trim() : null
        ADDITIONAL_TEST_LABEL = map.get("ADDITIONAL_TEST_LABEL") != null ? map.get("ADDITIONAL_TEST_LABEL").trim() : null
        KEEP_TEST_REPORTDIR = map.get("KEEP_TEST_REPORTDIR")
        ACTIVE_NODE_TIMEOUT = map.get("ACTIVE_NODE_TIMEOUT") != null ? map.get("ACTIVE_NODE_TIMEOUT").trim() : null
        CODEBUILD = map.get("CODEBUILD")
        DOCKER_IMAGE = map.get("DOCKER_IMAGE") != null ? map.get("DOCKER_IMAGE").trim() : null
        DOCKER_FILE = map.get("DOCKER_FILE") != null ? map.get("DOCKER_FILE").trim() : null
        DOCKER_NODE = map.get("DOCKER_NODE") != null ? map.get("DOCKER_NODE").trim() : null
        DOCKER_REGISTRY = map.get("DOCKER_REGISTRY") != null ? map.get("DOCKER_REGISTRY").trim() : null
        DOCKER_CREDENTIAL = map.get("DOCKER_CREDENTIAL") != null ? map.get("DOCKER_CREDENTIAL").trim() : null
        PLATFORM_CONFIG_LOCATION = map.get("PLATFORM_CONFIG_LOCATION") != null ? map.get("PLATFORM_CONFIG_LOCATION").trim() : null
        CONFIGURE_ARGS = map.get("CONFIGURE_ARGS") != null ? map.get("CONFIGURE_ARGS").trim() : null
        OVERRIDE_FILE_NAME_VERSION = map.get("OVERRIDE_FILE_NAME_VERSION") != null ? map.get("OVERRIDE_FILE_NAME_VERSION").trim() : null
        USE_ADOPT_SHELL_SCRIPTS = map.get("USE_ADOPT_SHELL_SCRIPTS")
        ADDITIONAL_FILE_NAME_TAG = map.get("ADDITIONAL_FILE_NAME_TAG") != null ? map.get("ADDITIONAL_FILE_NAME_TAG").trim() : null
        JDK_BOOT_VERSION = map.get("JDK_BOOT_VERSION") != null ? map.get("JDK_BOOT_VERSION").trim() : null
        RELEASE = map.get("RELEASE")
        PUBLISH_NAME = map.get("PUBLISH_NAME") != null ? map.get("PUBLISH_NAME").trim() : null
        ADOPT_BUILD_NUMBER = map.get("ADOPT_BUILD_NUMBER") != null ? map.get("ADOPT_BUILD_NUMBER").trim() : null
        ENABLE_TESTS = map.get("ENABLE_TESTS")
        ENABLE_TESTDYNAMICPARALLEL = map.get("ENABLE_TESTDYNAMICPARALLEL")
        ENABLE_INSTALLERS = map.get("ENABLE_INSTALLERS")
        ENABLE_SIGNER = map.get("ENABLE_SIGNER")
        ENABLE_SBOM = map.get("ENABLE_SBOM")
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
                ENABLE_SBOM               : ENABLE_SBOM,
                CLEAN_WORKSPACE           : CLEAN_WORKSPACE,
                CLEAN_WORKSPACE_AFTER     : CLEAN_WORKSPACE_AFTER,
                CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER : CLEAN_WORKSPACE_BUILD_OUTPUT_ONLY_AFTER
        ]
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
