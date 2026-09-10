import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class Jdk27PlusConfigurationTest {

    private Map<String, ?> loadConfigurationFile(String filename) {
        def script = new GroovyShell().parse(new File("jobs/configurations/${filename}"))
        def result = script.run()
        (Map<String, ?>) (result instanceof Map ? result : script.getProperty('targetConfigurations'))
    }

    @Test
    void jdk27AndLaterTargetConfigurationsDoNotIncludeX64Mac() {
        ['jdk27.groovy', 'jdk27_release.groovy', 'jdk28.groovy', 'jdk28_release.groovy'].each { filename ->
            Map<String, ?> config = loadConfigurationFile(filename)
            Assertions.assertFalse(config.containsKey('x64Mac'), "${filename} should not include x64Mac")
        }
    }

    @Test
    void jdk27AndLaterBuildConfigurationsDoNotIncludeX64Mac() {
        ['jdk27_pipeline_config.groovy', 'jdk28_pipeline_config.groovy'].each { filename ->
            Map<String, ?> config = loadConfigurationFile(filename)
            Assertions.assertFalse(config.containsKey('x64Mac'), "${filename} should not include x64Mac")
        }
    }
}
