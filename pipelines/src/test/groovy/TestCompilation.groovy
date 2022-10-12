import groovy.transform.TypeChecked
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

import java.util.regex.Pattern

class TestCompilation {

    private String setType(String code, String varName, String type) {
        return code.replaceAll(Pattern.compile("def ${varName}"), "${type} ${varName}")
    }

    private String getBuildFile(String filename) {
        def file = new File("../../../build/${filename}")

        if (!file.exists()) {
            file = new File("build/${filename}")
        }

        String code = file.getText('UTF-8')
        code = code.replaceAll(Pattern.compile('\nclass '), "\n@groovy.transform.TypeChecked(extensions = ['JenkinsTypeCheckHelperExtension']) class ")

        return code
    }

    private void doCompile(String name, Class argsClass) {
        try {
            def code = getBuildFile(name)

            def config = new CompilerConfiguration()

            config.setTargetDirectory(File.createTempDir())

            config.addCompilationCustomizers(
                    new ASTTransformationCustomizer(
                            TypeChecked)
            )

            def shell = new GroovyShell()

            if (argsClass != null) {
                argsClass.getDeclaredFields().each { key ->
                    shell.setVariable(key.getName(), new String())
                }
            }

            shell.evaluate(code, name)
        } catch (Exception e) {
            println('This test checks compilation against Stub implementations that Mimic the jenkins Environment')
            println('Check that any methods you wish to use from the environment are represented in the testDoubles package')
            e.printStackTrace()
            throw e
        }
    }

}
