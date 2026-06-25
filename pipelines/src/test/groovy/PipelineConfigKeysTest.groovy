import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PipelineConfigKeysTest {

    @Test
    void jdkPipelineConfigsDoNotDefineRedundantTestKeys() {
        def configurationsDir = new File('jobs/configurations')
        def pipelineConfigs = configurationsDir.listFiles().findAll { file ->
            file.name ==~ /jdk.*_pipeline_config\.groovy/
        }

        Assertions.assertFalse(pipelineConfigs.isEmpty(), 'No pipeline config files found')

        def redundantKeys = ['additionalTestLabels', 'additionalTestParams', 'test']
        pipelineConfigs.each { file ->
            def contents = file.getText('UTF-8')
            redundantKeys.each { key ->
                def matcher = contents =~ /(?m)^\s*${key}\s*:/
                Assertions.assertFalse(
                        matcher.find(),
                        "${file.name} still defines redundant key '${key}'"
                )
            }
        }
    }
}
