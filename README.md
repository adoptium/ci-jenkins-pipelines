<!-- textlint-disable terminology -->
# Jenkins pipeline files for building OpenJDK

Eclipse Adoptium makes use of these scripts to build binaries on the build farm at <https://ci.adoptopenjdk.net>

## Repository contents

This repository contains several useful scripts in order to build OpenJDK
personally or at build farm scale.

1. The `docs` folder contains images and utility scripts to produce up to date
documentation.
2. The `pipelines` folder contains the Groovy pipeline scripts for Jenkins
(e.g. build | test | checksum | release).
3. The `tools` folder contains `pipelines/` analysis scripts that deliever success/failure trends and build scripts for code-tool dependancies for the build and test process (e.g. asmtools | jcov | jtharness | jtreg | sigtest).

## Configuration Files

The [configurations](pipelines/jobs/configurations) directory contains two categories of configuration files that our jenkins pipelines use (Nicknamed [Build Configs](#build) and [Nightly Configs](#nightly) for short).

To ensure both configurations are not overridden in a race condition scenario by another job, the [job generators](pipelines/build/regeneration/README.md) ensure they remain in the sync with the repository.

**Generally, any new parameters/configurations that effect the jenkins environment directly should be implemented here.** If this is not the case, it would likely be better placed in [temurin-build/platform-specific-configurations](https://github.com/adoptium/temurin-build/tree/master/build-farm/platform-specific-configurations) (for OS or `make-adopt-build-farm.sh` specific use cases) or [temurin-build/build.sh](https://github.com/adoptium/temurin-build/blob/master/sbin/build.sh) (for anyone, including end users and jenkins pipelines).

### Build

The build config files are the ones that follow the format `jdkxx(u)_pipeline_config.groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository. Each is a groovy class with a single `Map<String, Map<String, ?>>` property containing node labels, tests and other jenkins parameters/constants that are crucial for allowing different parts of the build pipeline to mesh together.

Each architecture/platform has it's own entry similar to the one below (for JDK8 x64 mac builds). The pipelines use the parent map key (e.g. `x64Mac`) to retrieve the data. See [#Data Fields](#data-fields) for the currently available fields you can utilise.

```groovy
x64Mac        : [
    os                   : 'mac',
    arch                 : 'x64',
    additionalNodeLabels : [
            temurin  : 'macos10.14',
            corretto : 'build-macstadium-macos1010-1',
            openj9   : 'macos10.14'
    ],
    test                 : 'default'
]
```

### Data fields

NOTE: When the `type` field implies a map, the `String` key of the inner map is the variant for that field. E.g:

```groovy
                additionalNodeLabels : [
                        temurin : 'xlc13&&aix710',
                        openj9 :  'xlc13&&aix715'
                ],
```

---

| Name                       | Required? | Type                              | <div style="width:500px">Description</div> |
| :------------------------- | :---: | :------------------------------------ | :----------------------------------------- |
| os                         | ✅    | `String`                              | Operating system tag that will identify the job on jenkins and determine which platforms configs to pull from temurin-build.<br>*E.g. `windows`, `solaris`* |
| arch                       | ✅    | `String`                              | Architecture tag that will identify the job on jenkins and determine which build params to use.<br>*E.g. `x64`, `sparcv9`, `x86-32`* |
| test                       | ❌    | `String`<br>**OR**<br>`Map<String, List>`<br>**OR**<br>`Map<String, List or Map<String, List>>`   | Case one: Tests to run against the binary after the build has completed. A `default` tag indicates that you want to run [whatever the default test nightly/release list is](https://github.com/adoptium/ci-jenkins-pipelines/blob/ab947ce6ab0ecd75ebfb95eb2f75facb83e4dc13/pipelines/build/common/build_base_file.groovy#L66-L88).<br><br>Case two: You can also [specify your own list for that particular platform (not variant)](https://github.com/adoptium/ci-jenkins-pipelines/blob/ab947ce6ab0ecd75ebfb95eb2f75facb83e4dc13/pipelines/jobs/configurations/jdk16_pipeline_config.groovy#L59-L64). <br><br>Case three: Or you can even [specify the list for that particular platform per variant](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk8u_pipeline_config.groovy#L78-L81). The list could be specific one `sanity.openjdk` or `default` (similar to the first case) or a map per nightly or release (similar to case two).
| testDynamic                 | ❌    | `Boolean`<br>**OR**<br>`Map<String, ?>` | PARALLEL=Dynamic parameter setting. False : no Parallel. Or you can set the parameters with or without variant.
| dockerImage                | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Builds the JDK inside a docker container. Should be a DockerHub identifier to pull from in case **dockerFile** is not specified.<br>*E.g. `adoptopenjdk/centos6_build_image`* |
| dockerFile                 | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Builds the JDK inside a docker container using the locally stored image file. Used in conjunction with **dockerImage** to specify a particular variant to build or pull.<br>*E.g. `pipelines/build/dockerFiles/cuda.dockerfile`* |
| dockerNode                 | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Specifies a specific jenkins docker node label to shift into to build the JDK.<br> *E.g. `sw.config.uid1000`* |
| dockerRegistry             | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Used for Docker login when pulling dockerImage from a custom Docker registry. Used in conjunction with **dockerImage**. Default (blank) will be DockerHub. Must also use dockerCredential. |
| dockerCredential           | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Used for Docker login when pulling a dockerImage. Value is the Jenkins credential ID for the username and password of the dockerRegistry. Used in conjunction with **dockerImage**. Can use with custom dockerRegistry or default DockerHub. Must use this if using a non-default registry. |
| additionalNodeLabels       | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Appended to the default constructed jenkins node label (often used to lock variants or build configs to specific machines). Jenkins will additionally search for a node with this tag as well as the default node label.<br>*E.g. `build-macstadium-macos1010-1`, `macos10.14`* |
| additionalTestLabels       | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Used by [aqa-tests](https://github.com/adoptium/aqa-tests/blob/2b6ee54f18021c38386cea65c552de4ea20a8d1c/buildenv/jenkins/testJobTemplate#L213) to lock specific tests to specific machine nodes (in the same manner as **additionalNodeLabels**)<br>*E.g. `!(centos6\|\|rhel6)`, `dragonwell`* |
| configureArgs              | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Configuration arguments that will ultimately be passed to OpenJDK's `./configure`<br>*E.g. `--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system`* |
| buildArgs                  | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Build arguments that will ultimately be passed to [temurin-build's ./makejdk-any-platform.sh](https://github.com/adoptium/temurin-build#the-makejdk-any-platformsh-script) script<br>*E.g. `--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system`* |
| additionalFileNameTag      | ❌    | `String`                              | Commonly used when building [large heap versions](https://adoptopenjdk.net/faq.html#:~:text=What%20are%20the%20OpenJ9%20%22Large,XL%20in%20the%20download%20filenames) of the binary, this tag will also be included in the jenkins job name and binary filename. Include this parameter if you have an "extra" variant that requires a different tagname<br>*E.g. `linuxXL`* |
| crossCompile               | ❌    | `String`<br>**OR**<br>`Map<String, String>` | Used when building on a cross compiled system, informing jenkins to treat it differently when retrieving the version and producing the binary. This value is also used to create the jenkins node label alongside the **arch** (similarly to **additionalNodeLabels**)<br>*E.g. `x64`* |
| bootJDK                    | ❌    | `String`                              | JDK version number to specify to temurin-build's `make-adopt-build-farm.sh` script, informing it to utilise a [predefined location of a boot jdk](https://github.com/adoptium/temurin-build/blob/2df732492b59b1606439505316c766edbb566cc2/build-farm/make-adopt-build-farm.sh#L115-L141)<br> *E.g. `8`, `11`* |
| platformSpecificConfigPath | ❌    | `String`                              | temurin-build repository path to pull the operating system configurations from inside [temurin-build's set-platform-specific-configurations.sh](https://github.com/adoptium/temurin-build/blob/master/build-farm/set-platform-specific-configurations.sh). Do not include the repository name or branch as this is prepended automatically.<br>*E.g. `pipelines/TestLocation/platform-specific-configurations`* |
| codebuild                  | ❌    | `Boolean`                             | Setting this field will tell jenkins to spin up an Azure or [AWS cloud](https://aws.amazon.com/codebuild/) machine, allowing the build to retrieve a machine not normally available on the Jenkins server. It does this by appending a `codebuild` flag to the jenkins label. |
| cleanWorkspaceAfterBuild   | ❌    | `Boolean`                             | Setting this field will tell jenkins to clean down the workspace after the build has completed. Particularly useful for AIX where disk space can be limited. |

### Nightly

The nightly config files are the ones that follow the format `jdkxx(u).groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository. Each is a simple groovy script that's contents can be [loaded in](https://www.jenkins.io/doc/pipeline/steps/workflow-cps/#load-evaluate-a-groovy-source-file-into-the-pipeline-script) and accessed by another script.

### Release pipeline/jobs

The release config files are the ones that follow the format `jdkxx(u)_release.groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository.

#### targetConfigurations

A single `Map<String, Map<String, String>>` variable containing what platforms and variants will be run in the nightly builds and releases (by default, this can be altered in jenkins parameters before executing a user build). If you are [creating your own](docs/UsingOurScripts.md) nightly config, you will need to ensure the key values of the upper map are the same as the key values in the corresponding [build config file](#build).
jdkxx(u)*.groovy

```groovy
targetConfigurations = [
        "x64Mac"        : [
                "temurin",
                "openj9"
        ],
        "x64Linux"      : [
                "temurin",
                "openj9",
                "corretto",
                "dragonwell"
        ],
        "x32Windows"    : [
                "temurin",
                "openj9"
        ],
        "x64Windows"    : [
                "temurin",
                "openj9",
                "dragonwell"
        ],
        "ppc64Aix"      : [
                "temurin",
                "openj9"
        ],
        "ppc64leLinux"  : [
                "temurin",
                "openj9"
        ],
        "s390xLinux"    : [
                "temurin",
                "openj9"
        ],
        "aarch64Linux"  : [
                "temurin",
                "openj9",
                "dragonwell"
        ],
        "arm32Linux"  : [
                "temurin"
        ],
        "sparcv9Solaris": [
                "temurin"
        ]
]
```

#### disableJob

If this is present, the jenkins generators will still create the top-level pipeline and downstream jobs but will set them as disabled.
jdkxx(u).groovy

```groovy
disableJob = true
```

#### triggerSchedule_nightly / triggerSchedule_weekly

[Cron expression](https://crontab.guru/) that defines when (and how often) nightly/evaluation and weekly/weekly-evaluation builds will be executed

```jdkxx(u)[_YY].groovy
triggerSchedule_nightly="TZ=UTC\n05 18 * * 1,3,5"
triggerSchedule_weekly="TZ=UTC\n05 12 * * 6"
```

Use below two ways can set the job never to run:

- do not set `triggerSchedule_nightly` or `triggerSchedule_weekly` in the groovy file
- untick `ENABLE_PIPELINE_SCHEDULE` option in the Jenkins job which calls `pipelines/build/regeneration/build_pipeline_generator.groovy`

#### weekly_release_scmReferences

Source control references (e.g. tags) to use in the weekly release builds
jdkxx(u).groovy

```groovy
weekly_release_scmReferences = [
        "temurin"        : "jdk8u282-b07",
        "openj9"         : "v0.24.0-release",
        "corretto"       : "",
        "dragonwell"     : ""
]
```

## Metadata

Alongside the built assets a metadata file will be created with info about the build. This will be a JSON document of the form:

```json
{
    "vendor": "Eclipse Adoptium",
    "os": "mac",
    "arch": "x64",
    "variant": "openj9",
    "version": {
        "minor": 0,
        "patch": null,
        "msi_product_version": "11.0.18.6",
        "security": 0,
        "pre": null,
        "adopt_build_number": 0,
        "major": 15,
        "version": "15+29-202007070926",
        "semver": "15.0.0+29.0.202007070926",
        "build": 29,
        "opt": "202007070926"
    },
    "scmRef": "<output of git describe OR buildConfig.SCM_REF>",
    "buildRef": "<build-repo-name/build-commit-sha>",
    "version_data": "jdk15",
    "binary_type": "debugimage",
    "sha256": "<shasum>",
    "full_version_output": "<output of java --version>",
    "makejdk_any_platform_args": "<output of configure to makejdk-any-platform.sh>",
    "configure_arguments": "<output of bash configure>"
}
```

The Metadata class is contained in the [Metadata.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/library/src/common/MetaData.groovy) file and the Json is constructed and written in the [openjdk_build_pipeline.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/common/openjdk_build_pipeline.groovy) file.

It is worth noting the additional tags on the SemVer is the Adoptium build number.

Below are all of the keys contained in the metadata file and some example values that can be present.

---

- `vendor:`
Example values: [`Eclipse Adoptium`, `Alibaba`, `Huawei`]

This tag is used to identify the vendor of the JDK being built, this value is set in the [build.sh](https://github.com/adoptium/temurin-build/blob/9fa328f89f7381ceda5549fe0834ce36c14cbf56/sbin/build.sh#L222) file and defaults to "Temurin".

---

- `os:`
Example values: [`windows`, `mac`, `linux`, `aix`, `solaris`, `alpine-linux`]

This tag identifies the operating system the JDK has been built on (and should be used on).

---

- `arch:`
Example values: [`aarch64`, `ppc64`, `s390x`, `x64`, `x86-32`, `arm`]

This tag identifies the architecture the JDK has been built on and it intended to run on.

---

- `variant:`
Example values: [`hotspot`, `temurin`, `openj9`, `corretto`, `dragonwell`, `bisheng`, `fast_startup`]

This tag identifies the JVM being used by the JDK, "dragonwell" itself is not a JVM but is currently considered a variant in its own right.

---

- `variant_version:`

This tag is used to identify a version number of the variant being built, it currently is exclusively used by OpenJ9 and has the following keys:

- `major:`
Example values: [`0`, `1`]

- `minor:`
Example values: [`22`, `23`, `24`]

- `security:`
Example values: [`0`, `1`]

- `tags:`
Example values: [`m1`, `m2`]

---

- `version:`

This tag contains the full version information of the JDK built, it uses the [VersionInfo.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/library/src/common/VersionInfo.groovy) class and the [ParseVersion.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/library/src/ParseVersion.groovy) class.

It contains the following keys:

- `minor:`
Example values: [`0`]

- `security:`
Example Values: [`0`, `9`, `252` `272`]

- `pre:`
Example values: [`null`]

- `adopt_build_number:`
Example values: [`0`]
If the `ADOPT_BUILD_NUMBER` parameter is used to build te JDK that value will appear here, otherwise a default value of 0 appears.

- `major:`
Example values: [`8`, `11`, `15`, `16`]

- `version:`
Example values: [`1.8.0_272-202010111709-b09`, `11.0.9+10-202010122348`, `14.0.2+11-202007272039`, `16+19-202010120348`]

- `semver:`
Example values: [`8.0.202+8.0.202008210941`, `11.0.9+10.0.202010122348`, `14.0.2+11.0.202007272039`, `16.0.0+19.0.202010120339`]
Formed from the major, minor, security, and build number by the [formSemver()](https://github.com/adoptium/ci-jenkins-pipelines/blob/805e76acbb8a994abc1fb4b7d582486d48117ee8/pipelines/library/src/common/VersionInfo.groovy#L123) function.

- `build:`
Example values: [`6`, `9`, `18`]
The OpenJDK build number for the JDK being built.

- `opt:`
Example values: [`202008210941`, `202010120348`, `202007272039`]

---

- `scmRef:`
Example values: [`dragonwell-8.4.4_jdk8u262-b10`, `jdk-16+19_adopt-61198-g59e3baa94ac`, `jdk-11.0.9+10_adopt-197-g11f44f68c5`, `23f997ca1`]

A reference the the base JDK repository being build, usually including a GitHub commit reference, i.e. `jdk-16+19_adopt-61198-g59e3baa94ac` links to <https://github.com/adoptium/jdk/commit/59e3baa94ac> via the commit SHA **59e3baa94ac**.

Values that only contain a commit reference such as `23f997ca1` are OpenJ9 commits on their respective JDK repositories, for example **23f997ca1** links to the commit <https://github.com/ibmruntimes/openj9-openjdk-jdk14/commit/23f997ca1>.

---

- `buildRef:`
Example values: [`temurin-build/fe0f2dba`, `temurin-build/f412a523`]
A reference to the build tools repository used to create the JDK, uses the format **repository-name**/**commit-SHA**.

---

- `version_data:`
Example values: [`jdk8u`, `jdk11u`, `jdk14u`, `jdk`]

---

- `binary_type:`
Example values: [`jdk`, `jre`, `debugimage`, `testimage`]

---

- `sha256:`
Example values: [`20278aa9459e7636f6237e85fcd68deec1f42fa90c6c541a2dfa127f4156d3e2`, `2f9700bd75a807614d6d525fbd8d016c609a9ea71bf1ffd5d4839f3c1c8e4b8e`]
A SHA to verify the contents of the JDK.

---

- `full_version_output:`
Example values:

```java
openjdk version \"1.8.0_252\"\nOpenJDK Runtime Environment (Alibaba Dragonwell 8.4.4) (build 1.8.0_252-202010111720-b06)\nOpenJDK 64-Bit Server VM (Alibaba Dragonwell 8.4.4) (build 25.252-b06, mixed mode)\n`
```

The full output of the command `java -version` for the JDK.

---

- `configure_arguments:`
The full output generated by `make/autoconf/configure` from the JDK built.

## Build status

Table generated with `generateBuildMatrix.sh`
<!-- markdownlint-disable -->
| Platform | Java 8 | Java 11 | Java 17 | Java 19 | Java HEAD|
|------|----|----|----|----|----|
| aix-ppc64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-aix-ppc64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-aix-ppc64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-aix-ppc64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-aix-ppc64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-aix-ppc64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-aix-ppc64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-aix-ppc64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-aix-ppc64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-aix-ppc64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-aix-ppc64-temurin) | 
| alpine-linux-aarch64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-alpine-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-alpine-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-alpine-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-alpine-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-alpine-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-alpine-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-alpine-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-alpine-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-alpine-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-alpine-linux-aarch64-temurin) | 
| alpine-linux-x64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-alpine-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-alpine-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-alpine-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-alpine-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-alpine-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-alpine-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-alpine-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-alpine-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-alpine-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-alpine-linux-x64-temurin) | 
| linux-aarch64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-aarch64-temurin) | 
| linux-arm-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-arm-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-arm-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-arm-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-arm-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-arm-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-arm-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-arm-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-arm-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-arm-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-arm-temurin) | 
| linux-ppc64le-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-ppc64le-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-ppc64le-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-ppc64le-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-ppc64le-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-ppc64le-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-ppc64le-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-ppc64le-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-ppc64le-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-ppc64le-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-ppc64le-temurin) | 
| linux-s390x-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-s390x-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-s390x-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-s390x-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-s390x-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-s390x-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-s390x-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-s390x-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-s390x-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-s390x-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-s390x-temurin) | 
| linux-x64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-x64-temurin) | 
| mac-aarch64-temurin | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-mac-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-mac-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-mac-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-mac-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-aarch64-temurin) | 
| mac-x64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-mac-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-mac-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-mac-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-mac-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-x64-temurin) |
| windows-x64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-windows-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-windows-x64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x64-temurin) | 
| windows-x86-32-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x86-32-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x86-32-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x86-32-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x86-32-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-x86-32-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-x86-32-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-windows-x86-32-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-windows-x86-32-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x86-32-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x86-32-temurin) |
| linux-riscv64-temurin | N/A | N/A | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk19u/jdk19u-linux-riscv64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk19u/job/jdk19u-linux-riscv64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-riscv64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-riscv64-temurin) | 
| linux-riscv64-temurin-cross | N/A | N/A | N/A | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-riscv64-temurin-cross)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-riscv64-temurin-cross) |
| windows-aarch64-temurin | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-aarch64-temurin) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-aarch64-temurin) | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-aarch64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-aarch64-temurin) |
| solaris-sparcv9-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-sparcv9-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-sparcv9-temurin) | N/A | N/A | N/A | N/A | 
| solaris-x64-temurin | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-x64-temurin)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-x64-temurin) | N/A | N/A | N/A | N/A | 
<!-- markdownlint-enable -->