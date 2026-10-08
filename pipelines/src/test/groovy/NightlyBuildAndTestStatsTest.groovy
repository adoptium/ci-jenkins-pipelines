import groovy.json.JsonOutput
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NightlyBuildAndTestStatsTest {

    private Script loadScript() {
        return new GroovyShell().parse(new File('../tools/nightly_build_and_test_stats.groovy'))
    }

    @Test
    void findsLatestPlatformBuildIncludingFailures() {
        def script = loadScript()
        def children = [
            [buildName: 'jdk21u-mac-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/mac/1/', timestamp: 1, buildResult: 'SUCCESS'],
            [buildName: 'jdk21u-mac-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/mac/2/', timestamp: 2, buildResult: 'FAILURE'],
            [buildName: 'jdk21u-mac-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/mac/0/', timestamp: 0, buildResult: 'FAILURE'],
            [buildName: 'jdk21u-mac-aarch64-temurin', buildUrl: 'https://ci.adoptium.net/job/arm-mac/1/', timestamp: 1],
            [buildName: 'jdk21u-mac-x64-openj9', buildUrl: 'https://ci.adoptium.net/job/openj9/1/', timestamp: 3],
            [buildName: 'jdk17u-mac-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/jdk17/1/', timestamp: 3],
            [buildName: 'jdk21u-linux-x64-temurin', timestamp: 3]
        ]
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            Assertions.assertEquals('https://trss.adoptium.net/api/getChildBuilds?parentId=pipeline-id', url.toString())
            Assertions.assertEquals('cookies', cookieJar)
            return JsonOutput.toJson(children)
        }

        def urls = script.getPlatformBuildUrls('https://trss.adoptium.net', 'jdk21u', 'hotspot', 'pipeline-id', 'cookies')

        Assertions.assertEquals([
            x64Mac: 'https://ci.adoptium.net/job/mac/2/',
            aarch64Mac: 'https://ci.adoptium.net/job/arm-mac/1/'
        ], urls)
    }

    @Test
    void handlesMissingPipelineAndChildBuilds() {
        def script = loadScript()
        Assertions.assertEquals([:], script.getPlatformBuildUrls('https://trss.adoptium.net', 'jdk21u', 'temurin', '', 'cookies'))
        script.metaClass.callWgetSafely = { String url, String cookieJar -> '[]' }
        Assertions.assertEquals([:], script.getPlatformBuildUrls('https://trss.adoptium.net', 'jdk21u', 'temurin', 'pipeline-id', 'cookies'))
        script.metaClass.callWgetSafely = { String url, String cookieJar -> '' }
        Assertions.assertEquals([:], script.getPlatformBuildUrls('https://trss.adoptium.net', 'jdk21u', 'temurin', 'pipeline-id', 'cookies'))
    }

    @Test
    void usesJdk8JobNamesForPortReleaseLabelsAndJdkForHead() {
        def script = loadScript()
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            JsonOutput.toJson([
                [buildName: 'jdk8u-alpine-linux-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/alpine/1/'],
                [buildName: 'jdk8u-linux-arm-temurin', buildUrl: 'https://ci.adoptium.net/job/arm/1/'],
                [buildName: 'jdk-mac-x64-temurin', buildUrl: 'https://ci.adoptium.net/job/head/1/']
            ])
        }
        ['alpine-jdk8u', 'aarch32-jdk8u'].each { release ->
            Assertions.assertEquals([
                x64AlpineLinux: 'https://ci.adoptium.net/job/alpine/1/',
                arm32Linux: 'https://ci.adoptium.net/job/arm/1/'
            ], script.getPlatformBuildUrls('https://trss.adoptium.net', release, 'temurin', 'pipeline-id', 'cookies'))
        }
        Assertions.assertEquals([x64Mac: 'https://ci.adoptium.net/job/head/1/'],
            script.getPlatformBuildUrls('https://trss.adoptium.net', 'jdk', 'temurin', 'pipeline-id', 'cookies'))
    }

    @Test
    void linksPlatformsToBuildOrPipelineWhilePreservingFileLists() {
        def script = loadScript()
        def message = script.formatMissingArtifacts([
            'x64Mac : jdk : .tar.gz',
            'x64Mac : jdk : .tar.gz.sig',
            'aarch64Mac : All : .All'
        ], [x64Mac: 'https://ci.adoptium.net/job/mac/2/'], 'https://ci.adoptium.net/job/openjdk21-pipeline/3/')

        Assertions.assertEquals(' :\n    *<https://ci.adoptium.net/job/mac/2/|x64Mac>*: jdk.tar.gz, jdk.tar.gz.sig' +
            '\n    *<https://ci.adoptium.net/job/openjdk21-pipeline/3/|aarch64Mac>*: All.All', message)
    }

    @Test
    void preservesPlainTextWhenNoBuildWasDiscovered() {
        def script = loadScript()
        Assertions.assertEquals(' :\n    *x64Mac*: jdk.tar.gz', script.formatMissingArtifacts(['x64Mac : jdk : .tar.gz'], [:], ''))
        Assertions.assertEquals('', script.formatMissingArtifacts([], [:], ''))
    }
}
