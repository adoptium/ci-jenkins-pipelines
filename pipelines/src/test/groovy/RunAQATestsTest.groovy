import common.IndividualBuildConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RunAQATestsTest {

    static class BuildContextStub {
        List<Map> buildInvocations = []
        Map currentBuild = [:]
        String JENKINS_URL = 'https://ci.example/'

        def string(Map args) {
            args
        }

        def build(Map args) {
            buildInvocations << args
            [absoluteUrl: 'https://ci.example/job/AQA_Test_Pipeline_RELEASE/123/', number: 123]
        }

        def echo(String message) {}
        def println(Object message) {}
    }

    @Test
    void releaseAqaTestsDoNotPassVendorTestParameters() {
        def defaultsJson = [
            repository: [
                build_branch: 'master',
                build_url   : 'https://github.com/adoptium/temurin-build.git',
                helper_ref  : 'master'
            ]
        ]
        def context = new BuildContextStub()
        def build = new Build(
            new IndividualBuildConfig([
                ARCHITECTURE           : 'x64',
                TARGET_OS              : 'linux',
                VARIANT                : 'temurin',
                JAVA_TO_BUILD          : 'jdk17u',
                SCM_REF                : 'refs/tags/jdk-17.0.0+1',
                AQA_REF                : 'aqa-release-branch',
                RELEASE                : true,
                USE_ADOPT_SHELL_SCRIPTS: true
            ]),
            [:],
            defaultsJson,
            defaultsJson,
            context,
            [BUILD_URL: 'https://ci.example/job/build/456/'],
            context.currentBuild
        )

        build.runAQATests('OpenJDK17U-jdk_x64_linux_hotspot_17.0.0_1.tar.gz')

        Assertions.assertEquals(1, context.buildInvocations.size())
        Assertions.assertEquals('AQA_Test_Pipeline_RELEASE', context.buildInvocations[0].job.toString())

        def params = context.buildInvocations[0].parameters.collectEntries { [(it.name): it.value] }

        Assertions.assertEquals(8, params.size())
        Assertions.assertFalse(params.containsKey('VENDOR_TEST_REPOS'))
        Assertions.assertFalse(params.containsKey('VENDOR_TEST_BRANCHES'))
        Assertions.assertFalse(params.containsKey('VENDOR_TEST_DIRS'))
        Assertions.assertEquals('release', params.BUILD_TYPE.toString())
        Assertions.assertEquals('17', params.JDK_VERSIONS.toString())
        Assertions.assertEquals('x86-64_linux', params.PLATFORMS.toString())
    }
}
