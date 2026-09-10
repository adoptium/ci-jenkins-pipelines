import groovy.lang.Binding
import groovy.lang.GroovyShell
import groovy.lang.Script
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class NightlyBuildAndTestStatsTest {

    private Script loadScript() {
        def binding = new Binding()
        binding.setVariable('node', { String label, Closure body -> })
        binding.setVariable('params', [:])
        binding.setVariable('env', [:])
        binding.setVariable('currentBuild', [:])
        binding.setVariable('WORKSPACE', System.getProperty('java.io.tmpdir'))
        binding.setVariable('echo', { Object ignored -> })

        def shell = new GroovyShell(binding)
        return shell.parse(resolveScriptFile())
    }

    private File resolveScriptFile() {
        def candidates = [
            new File('../tools/nightly_build_and_test_stats.groovy'),
            new File('tools/nightly_build_and_test_stats.groovy')
        ]

        def scriptFile = candidates.find { it.exists() }
        assert scriptFile != null : 'Could not find tools/nightly_build_and_test_stats.groovy from the current test working directory'
        return scriptFile
    }

    @Test
    void formatsAqaSummaryWithPassRatesAndOmitsZeroCounts() {
        def script = loadScript()

        def summary = script.formatAqaSummary(
                [success: 8, warning: 1, failure: 1],
                [success: 12, warning: 0, failure: 0],
                [
                    core: [success: 3, warning: 1, failure: 0],
                    dev : [success: 0, warning: 0, failure: 0],
                    other: [success: 0, warning: 0, failure: 0]
                ]
        )

        assertTrue(summary.contains('**AQA Tests** | **Jobs**: 80% pass (8 :white_check_mark: · 1 :warning: · 1 :x:)&nbsp; |&nbsp; **Targets**: 100% pass (12 :white_check_mark:)'))
        assertTrue(summary.contains('**AQA Remote Tests** | **Core**: 75% pass (3 :white_check_mark: · 1 :warning:)'))
        assertFalse(summary.contains('**Dev**'))
        assertFalse(summary.contains('12 :x:'))
    }

    @Test
    void returnsNoAqaTestsRunWhenAllCountsAreZero() {
        def script = loadScript()

        def summary = script.formatAqaSummary(
                [success: 0, warning: 0, failure: 0],
                [success: 0, warning: 0, failure: 0],
                [
                    core: [success: 0, warning: 0, failure: 0],
                    dev : [success: 0, warning: 0, failure: 0],
                    other: [success: 0, warning: 0, failure: 0]
                ]
        )

        assertEquals(' _No AQA tests run._', summary)
    }

    @Test
    void formatsRoundedPercentagesWithoutTrailingZeros() {
        def script = loadScript()

        assertEquals('33.3', script.formatPassRatePercentage(1, 3))
        assertEquals('66.7', script.formatPassRatePercentage(2, 3))
        assertEquals('50', script.formatPassRatePercentage(1, 2))
    }

    @Test
    void returnsZeroedRemoteCountsForEmptyOrInvalidPayloads() {
        def script = loadScript()
        def zeroCounts = [
            core: [success: 0, warning: 0, failure: 0],
            dev : [success: 0, warning: 0, failure: 0],
            other: [success: 0, warning: 0, failure: 0]
        ]

        script.metaClass.callWgetSafely = { String url, String cookieJar -> '[]' }
        assertEquals(zeroCounts, script.getRemoteJckResults('https://example.invalid', 'https://example.invalid/job/AQA_Test_Pipeline_JCK/', 123, 'cookie-jar'))

        script.metaClass.callWgetSafely = { String url, String cookieJar -> '{"error":"missing"}' }
        assertEquals(zeroCounts, script.getRemoteJckResults('https://example.invalid', 'https://example.invalid/job/AQA_Test_Pipeline_JCK/', 123, 'cookie-jar'))
    }

    @Test
    void collectsUnstableTargetCountsWhenPresentInPipelineSummary() {
        def script = loadScript()
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            if (url.contains('getAllChildBuilds')) {
                return '''
                    [
                      {
                        "buildResult":"UNSTABLE",
                        "testSummary":{"passed":7,"unstable":2,"failed":1,"disabled":3}
                      }
                    ]
                '''
            }
            return '''
                [
                  {"buildName":"linux-x64-temurin","buildResult":"SUCCESS"}
                ]
            '''
        }

        def testResults = script.getPipelineTestResults('https://example.invalid', 'open17-pipeline', 'https://example.invalid/job/open17-pipeline/', '123', 'temurin', '_hs_', 'cookie-jar')

        assertEquals(7, testResults.testTargetPassed)
        assertEquals(2, testResults.testTargetUnstable)
        assertEquals(1, testResults.testTargetFailed)
        assertEquals(3, testResults.testTargetDisabled)
    }
}
