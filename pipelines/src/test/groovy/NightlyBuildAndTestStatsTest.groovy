import groovy.json.JsonOutput
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NightlyBuildAndTestStatsTest {

    private Script loadScript() {
        return new GroovyShell().parse(new File('../tools/nightly_build_and_test_stats.groovy'))
    }

    private Map pipeline(int number, Map targets, String tag = 'jdk-21.0.13+8_adopt') {
        return [
            _id: "pipeline-${number}", buildUrl: "https://ci.adoptium.net/job/openjdk21-pipeline/${number}/",
            timestamp: number, status: 'Done', buildNum: number,
            buildParams: [
                [name: 'releaseType', value: 'Weekly'],
                [name: 'overridePublishName', value: 'jdk-21.0.13+8-ea'],
                [name: 'scmReference', value: tag],
                [name: 'targetConfigurations', value: JsonOutput.toJson(targets)]
            ]
        ]
    }

    @Test
    void selectsLatestPipelineConfiguredForEachPlatformAndVariant() {
        def script = loadScript()
        def history = [
            pipeline(500, [aarch64Windows: ['temurin'], x64Mac: ['temurin']]),
            pipeline(504, [aarch64Windows: ['temurin']], 'another-tag'),
            pipeline(501, [aarch64Windows: ['openj9'], x64Linux: ['temurin']]),
            pipeline(499, [aarch64Windows: ['temurin']]),
            pipeline(502, [x64Linux: ['temurin'], aarch64WindowsOther: ['temurin']])
        ]
        def childRequests = []
        script.metaClass.echo = { Object message -> }
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            if (url.contains('getBuildHistory')) {
                return JsonOutput.toJson(history)
            }
            childRequests.add(url.toString())
            Assertions.assertTrue(url.endsWith('parentId=pipeline-500') || url.endsWith('parentId=pipeline-502'))
            return JsonOutput.toJson([
                [buildName: 'jdk21u-windows-aarch64-temurin', buildUrl: 'https://ci.adoptium.net/job/windows/10/', buildResult: 'FAILURE']
            ])
        }

        Assertions.assertEquals('https://ci.adoptium.net/job/openjdk21-pipeline/502/',
            script.getBuildUrls('https://trss.adoptium.net', 'temurin', 'jdk21u', 'jdk-21.0.13+8-ea',
                'jdk-21.0.13+8_adopt', true, [], 'cookies')[0][0])
        def urls = script.getMissingArtifactBuildUrls('https://trss.adoptium.net', 'jdk21u', 'jdk21u', 'temurin',
            'jdk-21.0.13+8-ea', 'jdk-21.0.13+8_adopt', [
                'aarch64Windows : All : .All', 'x64Mac : jdk : .tar.gz', 'x64Linux : All : .All',
                'aarch64Windows : jdk : .tar.gz', 'ppc64Aix : All : .All'
            ], 'cookies')

        Assertions.assertEquals([
            aarch64Windows: 'https://ci.adoptium.net/job/windows/10/',
            x64Mac: 'https://ci.adoptium.net/job/openjdk21-pipeline/500/',
            x64Linux: 'https://ci.adoptium.net/job/openjdk21-pipeline/502/'
        ], urls)
        Assertions.assertEquals(2, childRequests.size())
        Assertions.assertEquals(' :\n    *ppc64Aix*: All.All',
            script.formatMissingArtifacts(['ppc64Aix : All : .All'], urls, ''))
    }

    @Test
    void fallsBackToConfiguredPipelineWhenChildLookupIsUnavailable() {
        def script = loadScript()
        script.metaClass.echo = { Object message -> }
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            url.contains('getBuildHistory') ? JsonOutput.toJson([
                pipeline(502, [x64Linux: ['temurin']]),
                pipeline(500, [aarch64Windows: ['temurin']])
            ]) : ''
        }
        def urls = script.getMissingArtifactBuildUrls('https://trss.adoptium.net', 'jdk21u', 'jdk21u', 'temurin',
            'jdk-21.0.13+8-ea', 'jdk-21.0.13+8_adopt', ['aarch64Windows : All : .All'], 'cookies')

        Assertions.assertEquals([aarch64Windows: 'https://ci.adoptium.net/job/openjdk21-pipeline/500/'], urls)
        Assertions.assertEquals(' :\n    *<https://ci.adoptium.net/job/openjdk21-pipeline/500/|aarch64Windows>*: All.All',
            script.formatMissingArtifacts(['aarch64Windows : All : .All'], urls, ''))
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
