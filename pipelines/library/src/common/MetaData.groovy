/*
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package common

class MetaData {
    final String vendor
    final String os
    final String arch
    final String variant
    final Map variant_version
    final VersionInfo version
    final String scmRef
    final String buildRef
    final String version_data
    final String full_version_output
    final String makejdk_any_platform_args
    final String configure_arguments
    final String make_command_args
    final String BUILD_CONFIGURATION_param
    final String openjdk_built_config
    final String openjdk_source
    final String build_env_docker_image_digest
    final String dependency_version_alsa
    final String dependency_version_freetype
    final String dependency_version_freemarker
    String binary_type
    String sha256

    MetaData(
        String vendor,
        String os,
        String scmRef,
        String buildRef,
        VersionInfo version,
        String version_data,
        String variant,
        Map variant_version,
        String arch,
        String full_version_output,
        String makejdk_any_platform_args,
        String configure_arguments,
        String make_command_args,
        String BUILD_CONFIGURATION_param,
        String openjdk_built_config,
        String openjdk_source,
        String build_env_docker_image_digest,
        String dependency_version_alsa,
        String dependency_version_freetype,
        String dependency_version_freemarker
    ) {
        this.vendor = vendor
        this.os = os
        this.scmRef = scmRef
        this.buildRef = buildRef
        this.version = version
        this.version_data = version_data
        this.variant = variant
        this.variant_version = variant_version
        this.arch = arch
        this.full_version_output = full_version_output
        this.makejdk_any_platform_args = makejdk_any_platform_args
        this.configure_arguments = configure_arguments
        this.make_command_args = make_command_args
        this.BUILD_CONFIGURATION_param = BUILD_CONFIGURATION_param
        this.openjdk_built_config = openjdk_built_config
        this.openjdk_source = openjdk_source
        this.build_env_docker_image_digest = build_env_docker_image_digest
        this.dependency_version_alsa = dependency_version_alsa
        this.dependency_version_freetype = dependency_version_freetype
        this.dependency_version_freemarker = dependency_version_freemarker
    }

    Map asMap() {
        def map = [
                vendor      : vendor,
                os          : os,
                arch        : arch,
                variant     : variant,
                version     : version,
                scmRef      : scmRef,
                buildRef    : buildRef,
                version_data: version_data,
                binary_type : binary_type,
                sha256      : sha256,
                full_version_output : full_version_output,
                makejdk_any_platform_args : makejdk_any_platform_args,
                configure_arguments : configure_arguments,
                make_command_args : make_command_args,
                BUILD_CONFIGURATION_param : BUILD_CONFIGURATION_param,
                openjdk_built_config : openjdk_built_config,
                openjdk_source : openjdk_source,
                build_env_docker_image_digest : build_env_docker_image_digest,
                dependency_version_alsa : dependency_version_alsa,
                dependency_version_freetype : dependency_version_freetype,
                dependency_version_freemarker : dependency_version_freemarker
            ]

        if (variant_version) {
            map.variant_version = variant_version
        }

        return map
    }
}
