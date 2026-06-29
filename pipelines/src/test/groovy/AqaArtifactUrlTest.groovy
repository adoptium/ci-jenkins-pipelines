import common.IndividualBuildConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AqaArtifactUrlTest {

    private static final String BUILD_URL = 'https://ci.example/job/build/123'

    private Build newBuild(String buildArgs = '') {
        new Build(
            new IndividualBuildConfig([BUILD_ARGS: buildArgs]),
            [:],
            [:],
            [:],
            this,
            [BUILD_URL: BUILD_URL],
            null
        )
    }

    @Test
    void onlyIncludesJdkAndTestImageWhenSbomIsDisabled() {
        def build = newBuild()
        def jdkFileName = 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz'

        Assertions.assertEquals(
            "${BUILD_URL}/artifact/workspace/target/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz " +
                "${BUILD_URL}/artifact/workspace/target/OpenJDK21U-testimage_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz",
            build.getAqaCustomizedSdkUrl(jdkFileName)
        )
    }

    @Test
    void includesSbomArtifactWhenSbomIsEnabled() {
        def build = newBuild('--create-sbom --some-other-flag')
        def jdkFileName = 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz'

        Assertions.assertEquals(
            "${BUILD_URL}/artifact/workspace/target/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz " +
                "${BUILD_URL}/artifact/workspace/target/OpenJDK21U-testimage_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz " +
                "${BUILD_URL}/artifact/workspace/target/OpenJDK21U-sbom_x64_linux_hotspot_21.0.12-beta+7-ea.json",
            build.getAqaCustomizedSdkUrl(jdkFileName)
        )
    }

    @Test
    void derivesSbomFileNameFromCommonArchiveExtensions() {
        def build = newBuild('--create-sbom')

        Assertions.assertEquals(
            'OpenJDK21U-sbom_x64_linux_hotspot_21.0.12-beta+7-ea.json',
            build.getSbomFileName('OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.tar.gz')
        )
        Assertions.assertEquals(
            'OpenJDK21U-sbom_x64_linux_hotspot_21.0.12-beta+7-ea.json',
            build.getSbomFileName('OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.zip')
        )
        Assertions.assertEquals(
            'OpenJDK21U-sbom_x64_linux_hotspot_21.0.12-beta+7-ea.json',
            build.getSbomFileName('OpenJDK21U-jdk_x64_linux_hotspot_21.0.12-beta+7-ea.msi')
        )
    }
}
