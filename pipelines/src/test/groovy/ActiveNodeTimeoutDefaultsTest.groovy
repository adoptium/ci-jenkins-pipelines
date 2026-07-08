import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ActiveNodeTimeoutDefaultsTest {

    @Test
    void pipelineTemplatesUseTenMinuteActiveNodeTimeoutByDefault() {
        File repoRoot = new File(System.getProperty('user.dir'))

        String pipelineJobTemplate = new File(repoRoot, 'jobs/pipeline_job_template.groovy').text
        String releasePipelineJobTemplate = new File(repoRoot, 'jobs/release_pipeline_job_template.groovy').text
        Assertions.assertTrue(pipelineJobTemplate.contains("stringParam('activeNodeTimeout', '10'"), 'pipeline_job_template.groovy should default activeNodeTimeout to 10 minutes')
        Assertions.assertTrue(releasePipelineJobTemplate.contains("stringParam('activeNodeTimeout', '10'"), 'release_pipeline_job_template.groovy should default activeNodeTimeout to 10 minutes')
    }
}
