import common.RepoHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RepoHandlerTest {

    private Map testRemote = [
        'branch'  : 'foo',
        'remotes' : [
            'url'         : 'https://github.com/bar/temurin-build.git',
            'credentials' : '1234567890'
        ]
    ]

    @Test
    void adoptDefaultsGetterReturns() {
        RepoHandler handler = new RepoHandler([:])
        Map adoptJson = handler.getAdoptDefaultsJson()

        // Repository
        Assertions.assertTrue(adoptJson.repository instanceof Map)
        Assertions.assertEquals(adoptJson.repository.build_url, 'https://github.com/adoptium/temurin-build.git')
        Assertions.assertEquals(adoptJson.repository.build_branch, 'master')
        Assertions.assertEquals(adoptJson.repository.pipeline_url, 'https://github.com/adoptium/ci-jenkins-pipelines.git')
        Assertions.assertEquals(adoptJson.repository.pipeline_branch, 'master')

        // Jenkins Details
        Assertions.assertTrue(adoptJson.jenkinsDetails instanceof Map)
        Assertions.assertEquals(adoptJson.jenkinsDetails.rootUrl, 'https://ci.adoptopenjdk.net')
        Assertions.assertEquals(adoptJson.jenkinsDetails.rootDirectory, 'build-scripts')

        // Templates
        Assertions.assertTrue(adoptJson.templateDirectories instanceof Map)
        Assertions.assertEquals(adoptJson.templateDirectories.downstream, 'pipelines/build/common/create_job_from_template.groovy')
        Assertions.assertEquals(adoptJson.templateDirectories.upstream, 'pipelines/jobs/pipeline_job_template.groovy')
        Assertions.assertEquals(adoptJson.templateDirectories.weekly, 'pipelines/jobs/weekly_release_pipeline_job_template.groovy')

        // Configs
        Assertions.assertTrue(adoptJson.configDirectories instanceof Map)
        Assertions.assertEquals(adoptJson.configDirectories.build, 'pipelines/jobs/configurations')
        Assertions.assertEquals(adoptJson.configDirectories.nightly, 'pipelines/jobs/configurations')
        Assertions.assertEquals(adoptJson.configDirectories.platform, 'build-farm/platform-specific-configurations')

        // Scripts
        Assertions.assertTrue(adoptJson.scriptDirectories instanceof Map)
        Assertions.assertEquals(adoptJson.scriptDirectories.upstream, 'pipelines/build')
        Assertions.assertEquals(adoptJson.scriptDirectories.downstream, 'pipelines/build/common/kick_off_build.groovy')
        Assertions.assertEquals(adoptJson.scriptDirectories.weekly, 'pipelines/build/common/weekly_release_pipeline.groovy')
        Assertions.assertEquals(adoptJson.scriptDirectories.regeneration, 'pipelines/build/common/config_regeneration.groovy')
        Assertions.assertEquals(adoptJson.scriptDirectories.tester, 'pipelines/build/prTester/pr_test_pipeline.groovy')
        Assertions.assertEquals(adoptJson.scriptDirectories.buildfarm, 'build-farm/make-adopt-build-farm.sh')

        // Base files
        Assertions.assertTrue(adoptJson.baseFileDirectories instanceof Map)
        Assertions.assertEquals(adoptJson.baseFileDirectories.upstream, 'pipelines/build/common/build_base_file.groovy')
        Assertions.assertEquals(adoptJson.baseFileDirectories.downstream, 'pipelines/build/common/openjdk_build_pipeline.groovy')

        // Defaults URL
        Assertions.assertEquals(adoptJson.defaultsUrl, 'https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/master/pipelines/defaults.json')

        // Test details
        Assertions.assertTrue(adoptJson.testDetails.enableTests instanceof Boolean)
        Assertions.assertTrue(adoptJson.testDetails.nightlyDefault instanceof List)
        Assertions.assertTrue(adoptJson.testDetails.weeklyDefault instanceof List)
    }

    @Test
    void userDefaultsSetterAndGetterReturns() {
        RepoHandler handler = new RepoHandler([:])
        String fakeDefaults = new File(System.getProperty('user.dir') + '/src/test/groovy/fakeDefaults.json').text
        handler.setUserDefaultsJson(this, fakeDefaults)
        Map userJson = handler.getUserDefaultsJson()

        // Repository
        Assertions.assertTrue(userJson.repository instanceof Map)
        Assertions.assertEquals(userJson.repository.build_url, '1')
        Assertions.assertEquals(userJson.repository.build_branch, '20')
        Assertions.assertEquals(userJson.repository.pipeline_url, '19')
        Assertions.assertEquals(userJson.repository.pipeline_branch, '21')

        // Jenkins Details
        Assertions.assertTrue(userJson.jenkinsDetails instanceof Map)
        Assertions.assertEquals(userJson.jenkinsDetails.rootUrl, '3')
        Assertions.assertEquals(userJson.jenkinsDetails.rootDirectory, '4')

        // Templates
        Assertions.assertTrue(userJson.templateDirectories instanceof Map)
        Assertions.assertEquals(userJson.templateDirectories.downstream, '5')
        Assertions.assertEquals(userJson.templateDirectories.upstream, '6')
        Assertions.assertEquals(userJson.templateDirectories.weekly, '7')

        // Configs
        Assertions.assertTrue(userJson.configDirectories instanceof Map)
        Assertions.assertEquals(userJson.configDirectories.build, '8')
        Assertions.assertEquals(userJson.configDirectories.nightly, '9')
        Assertions.assertEquals(userJson.configDirectories.platform, '10')

        // Scripts
        Assertions.assertTrue(userJson.scriptDirectories instanceof Map)
        Assertions.assertEquals(userJson.scriptDirectories.upstream, '11')
        Assertions.assertEquals(userJson.scriptDirectories.downstream, '12')
        Assertions.assertEquals(userJson.scriptDirectories.weekly, '13')
        Assertions.assertEquals(userJson.scriptDirectories.regeneration, '14')
        Assertions.assertEquals(userJson.scriptDirectories.tester, '15')
        Assertions.assertEquals(userJson.scriptDirectories.buildfarm, '22')

        // Base files
        Assertions.assertTrue(userJson.baseFileDirectories instanceof Map)
        Assertions.assertEquals(userJson.baseFileDirectories.upstream, '16')
        Assertions.assertEquals(userJson.baseFileDirectories.downstream, '17')

        // Defaults URL
        Assertions.assertEquals(userJson.defaultsUrl, '23')

        // Test details
        Assertions.assertTrue(userJson.testDetails.enableTests instanceof Boolean)
        Assertions.assertTrue(userJson.testDetails.enableTests)
        Assertions.assertEquals(userJson.testDetails.nightlyDefault, [ 'test1', 'test2', 'test3' ])
        Assertions.assertEquals(userJson.testDetails.weeklyDefault, [ 'test4', 'test5', 'test6' ])
    }

    @Test
    void userConfigGetterReturns() {
        RepoHandler handler = new RepoHandler(testRemote)
        Map userConfigsMap = handler.getUserRemoteConfigs()

        Assertions.assertEquals(userConfigsMap.branch, 'foo')
        Assertions.assertTrue(userConfigsMap.remotes instanceof Map)
        Assertions.assertEquals(userConfigsMap.remotes.url, 'https://github.com/bar/temurin-build.git')
        Assertions.assertEquals(handler.configs.remotes.credentials, '1234567890')
    }

}
