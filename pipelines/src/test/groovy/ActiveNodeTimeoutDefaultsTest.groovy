import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ActiveNodeTimeoutDefaultsTest {

    @Test
    void pipelineTemplatesUseTenMinuteActiveNodeTimeoutByDefault() {
        String repoRoot = System.getProperty('user.dir')

        String pipelineJobTemplate = new File(repoRoot + '/jobs/pipeline_job_template.groovy').text
        String releasePipelineJobTemplate = new File(repoRoot + '/jobs/release_pipeline_job_template.groovy').text
        String prTestPipeline = new File(repoRoot + '/build/prTester/pr_test_pipeline.groovy').text

        Assertions.assertTrue(pipelineJobTemplate.contains("stringParam('activeNodeTimeout', '10'"))
        Assertions.assertTrue(releasePipelineJobTemplate.contains("stringParam('activeNodeTimeout', '10'"))
        Assertions.assertTrue(prTestPipeline.contains("context.string(name: 'activeNodeTimeout', value: '10')"))
    }
}
