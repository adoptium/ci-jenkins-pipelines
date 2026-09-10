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
                    dev : [success: 0, warning: 0, failure: 0]
                ]
        )

        assertTrue(summary.contains('**AQA Tests** | **Jobs**: 80% pass (8 :white_check_mark: · 1 :warning: · 1 :x:)&nbsp; |&nbsp; **Targets**: 100% pass (12 :white_check_mark:)'))
        assertTrue(summary.contains('**AQA Remote Tests** | **Core**: 75% pass (3 :white_check_mark: · 1 :warning:)'))
        assertFalse(summary.contains('**Dev**'))
        assertFalse(summary.contains('12 :x:'))
    }

    @Test
    void groupsRemoteJckTargetsIntoCoreAndDev() {
        def script = loadScript()
        script.metaClass.callWgetSafely = { String url, String cookieJar ->
            return '''
                [
                  {"target":"sanity","buildResult":"SUCCESS"},
                  {"target":"special","buildResult":"UNSTABLE"},
                  {"target":"extended","buildResult":"FAILURE"},
                  {"target":"dev","buildResult":"SUCCESS"},
                  {"target":"dev","buildResult":"ABORTED"},
                  {"target":"unknown","buildResult":"SUCCESS"},
                  {"target":"dev","buildResult":null}
                ]
            '''
        }

        def remoteCounts = script.getRemoteJckResults('https://example.invalid', 'https://example.invalid/job/AQA_Test_Pipeline_JCK/', 123, 'cookie-jar')

        assertEquals([success: 1, warning: 1, failure: 1], remoteCounts.core)
        assertEquals([success: 1, warning: 0, failure: 1], remoteCounts.dev)
    }
}
