<!-- textlint-disable terminology -->
# Jenkins pipeline files for building OpenJDK

Eclipse Adoptium makes use of these scripts to build binaries on the build farm at <https://ci.adoptium.net>

## Repository contents

This repository contains several useful scripts in order to build OpenJDK
personally or at build farm scale.

1. The `docs` folder contains images and utility scripts to produce up to date
documentation.
2. The `pipelines` folder contains the Groovy pipeline scripts for Jenkins
(e.g. build | test | checksum | release).
3. The `tools` folder contains `pipelines/` analysis scripts that deliever success/failure trends and build scripts for code-tool dependancies for the build and test process (e.g. asmtools | jcov | jtharness | jtreg | sigtest).

## Configuration Files

The [pipelines/jobs/configurations](pipelines/jobs/configurations) directory contains two categories of configuration files that our jenkins pipelines use (Nicknamed [#Build Configs](#build) and [#Nightly Configs](#nightly) for short).

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

| Name                       | Required? | Type                                        | <div style="width:500px">Description</div> |
| :------------------------- | :-------: | :------------------------------------------ | :----------------------------------------- |
| os                         | &#9989;   | `String`                                    | Operating system tag that will identify the job on jenkins and determine which platforms configs to pull from temurin-build.<br>*E.g. `windows`, `solaris`* |
| arch                       | &#9989;   | `String`                                    | Architecture tag that will identify the job on jenkins and determine which build params to use.<br>*E.g. `x64`, `sparcv9`, `x86-32`* |
| test                       | &#10060;  | `String`<br>**OR**<br>`Map<String, List>`<br>**OR**<br>`Map<String, List or Map<String, List>>`   | Case one: Tests to run against the binary after the build has completed. A `default` tag indicates that you want to run [whatever the default test nightly/release list is](https://github.com/adoptium/ci-jenkins-pipelines/blob/ab947ce6ab0ecd75ebfb95eb2f75facb83e4dc13/pipelines/build/common/build_base_file.groovy#L66-L88).<br><br>Case two: You can also [specify your own list for that particular platform (not variant)](https://github.com/adoptium/ci-jenkins-pipelines/blob/ab947ce6ab0ecd75ebfb95eb2f75facb83e4dc13/pipelines/jobs/configurations/jdk16_pipeline_config.groovy#L59-L64). <br><br>Case three: Or you can even [specify the list for that particular platform per variant](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk8u_pipeline_config.groovy#L78-L81). The list could be specific one `sanity.openjdk` or `default` (similar to the first case) or a map per nightly or release (similar to case two). |
| testDynamic                | &#10060;  | `Boolean`<br>**OR**<br>`Map<String, ?>`     | PARALLEL=Dynamic parameter setting. False : no Parallel. Or you can set the parameters with or without variant.
| dockerImage                | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Builds the JDK inside a docker container. Should be a DockerHub identifier to pull from in case **dockerFile** is not specified.<br>*E.g. `adoptopenjdk/centos6_build_image`* |
| dockerFile                 | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Builds the JDK inside a docker container using the locally stored image file. Used in conjunction with **dockerImage** to specify a particular variant to build or pull.<br>*E.g. `pipelines/build/dockerFiles/cuda.dockerfile`* |
| dockerNode                 | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Specifies a specific jenkins docker node label to shift into to build the JDK.<br> *E.g. `sw.config.uid1000`* |
| dockerRegistry             | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Used for Docker login when pulling dockerImage from a custom Docker registry. Used in conjunction with **dockerImage**. Default (blank) will be DockerHub. Must also use dockerCredential. |
| dockerCredential           | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Used for Docker login when pulling a dockerImage. Value is the Jenkins credential ID for the username and password of the dockerRegistry. Used in conjunction with **dockerImage**. Can use with custom dockerRegistry or default DockerHub. Must use this if using a non-default registry. |
| additionalNodeLabels       | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Appended to the default constructed jenkins node label (often used to lock variants or build configs to specific machines). Jenkins will additionally search for a node with this tag as well as the default node label.<br>*E.g. `build-macstadium-macos1010-1`, `macos10.14`* |
| additionalTestLabels       | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Used by [aqa-tests](https://github.com/adoptium/aqa-tests/blob/2b6ee54f18021c38386cea65c552de4ea20a8d1c/buildenv/jenkins/testJobTemplate#L213) to lock specific tests to specific machine nodes (in the same manner as **additionalNodeLabels**)<br>*E.g. `!(centos6\|\|rhel6)`, `dragonwell`* |
| configureArgs              | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Configuration arguments that will ultimately be passed to OpenJDK's `./configure`<br>*E.g. `--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system`* |
| buildArgs                  | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Build arguments that will ultimately be passed to [temurin-build's ./makejdk-any-platform.sh](https://github.com/adoptium/temurin-build#the-makejdk-any-platformsh-script) script<br>*E.g. `--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system`* |
| additionalFileNameTag      | &#10060;  | `String`                                    | Commonly used when building [large heap versions](https://adoptopenjdk.net/faq.html#:~:text=What%20are%20the%20OpenJ9%20%22Large,XL%20in%20the%20download%20filenames) of the binary, this tag will also be included in the jenkins job name and binary filename. Include this parameter if you have an "extra" variant that requires a different tagname<br>*E.g. `linuxXL`* |
| crossCompile               | &#10060;  | `String`<br>**OR**<br>`Map<String, String>` | Used when building on a cross compiled system, informing jenkins to treat it differently when retrieving the version and producing the binary. This value is also used to create the jenkins node label alongside the **arch** (similarly to **additionalNodeLabels**)<br>*E.g. `x64`* |
| bootJDK                    | &#10060;  | `String`                                    | JDK version number to specify to temurin-build's `make-adopt-build-farm.sh` script, informing it to utilise a [predefined location of a boot jdk](https://github.com/adoptium/temurin-build/blob/2df732492b59b1606439505316c766edbb566cc2/build-farm/make-adopt-build-farm.sh#L115-L141)<br> *E.g. `8`, `11`* |
| platformSpecificConfigPath | &#10060;  | `String`                                    | temurin-build repository path to pull the operating system configurations from inside [temurin-build's set-platform-specific-configurations.sh](https://github.com/adoptium/temurin-build/blob/master/build-farm/set-platform-specific-configurations.sh). Do not include the repository name or branch as this is prepended automatically.<br>*E.g. `pipelines/TestLocation/platform-specific-configurations`* |
| codebuild                  | &#10060;  | `Boolean`                                   | Setting this field will tell jenkins to spin up an Azure or [AWS cloud](https://aws.amazon.com/codebuild/) machine, allowing the build to retrieve a machine not normally available on the Jenkins server. It does this by appending a `codebuild` flag to the jenkins label. |
| cleanWorkspaceAfterBuild   | &#10060;  | `Boolean`                                   | Setting this field will tell jenkins to clean down the workspace after the build has completed. Particularly useful for AIX where disk space can be limited. |

### Nightly (beta/non-release)

The nightly or beta/non-release  config files are the ones that follow the format `jdkxx(u).groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository. Each is a simple groovy script that's contents can be [loaded in](https://www.jenkins.io/doc/pipeline/steps/workflow-cps/#load-evaluate-a-groovy-source-file-into-the-pipeline-script) and accessed by another script.

### Evaluation pipeline/jobs

The evaluation config files are the ones that follow the format `jdkxx(u)_evaluation.groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository.

#### targetConfigurations

A single `Map<String, Map<String, String>>` variable containing what platforms and variants will be run in the nightly builds, evaluation builds and releases (by default, this can be altered in jenkins parameters before executing a user build). If you are [creating your own](docs/UsingOurScripts.md) nightly config, you will need to ensure the key values of the upper map are the same as the key values in the corresponding [build config file](#build).

### Release pipeline/jobs

The release config files are the ones that follow the format `jdkxx(u)_release.groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository.
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

#### triggerSchedule_nightly / triggerSchedule_weekly / triggerSchedule_evaluation / triggerSchedule_weekly_evaluation

All JDK versions now support "beta" EA triggered builds from the publication of upstream build tags. Eclipse Adoptium no
longer runs scheduled nightly/weekend builds.

The one exception to this is Oracle managed STS versions, whose builds are managed internal to Oracle and not published
until the GA day. For these a triggerSchedule_weekly is required to build the upstream HEAD commits on a regular basis.

[Cron expression](https://crontab.guru/) that defines when (and how often) nightly/evaluation and weekly/weekly-evaluation builds will be executed

in jdkxx(u).groovy

```groovy
triggerSchedule_nightly="TZ=UTC\n05 18 * * 1,3,5"
triggerSchedule_weekly="TZ=UTC\n05 12 * * 6"
```

in jdkXX(u)_evaluation.groovy

```groovy
triggerSchedule_evaluation="TZ=UTC\n15 18 * * 1,3,5"
triggerSchedule_weekly_evaluation="TZ=UTC\n25 12 * * 6"
```

#### weekly_release_scmReferences / weekly_evaluation_scmReferences

Source control references (e.g. tags) to use in the scheduled weekly release or weekly evaluation builds
in jdkXX(u).groovy
Use below two ways can set the job never to run:

- do not set `triggerSchedule_nightly` or `triggerSchedule_weekly` in the groovy file
- untick `ENABLE_PIPELINE_SCHEDULE` option in the Jenkins job which calls `pipelines/build/regeneration/build_pipeline_generator.groovy`

#### weekly_release_scmReferences

Source control references (e.g. tags) to use in the scheduled weekly release builds
jdkxx(u).groovy

```groovy
weekly_release_scmReferences = [
        "temurin"        : "jdk8u282-b08"
]
```

in jdkXX(u)_evaluation.groovy

```groovy
weekly_evaluation_scmReferences== [
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

## Tag driven "beta" EA builds

### pipelines/build/common/trigger_beta_build.groovy

Jenkins pipeline to automate triggering of "beta" EA builds from the publication of upstream build tags. Eclipse Adoptium no
longer runs scheduled nightly/weekend builds, instead pipeline builds are triggered by this job.

The one exception to this is Oracle managed STS versions, whose builds are managed internal to Oracle and not published
until the GA day. For these a triggerSchedule_weekly is required to build the upstream HEAD commits on a regular basis.

pipelines/build/common/trigger_beta_build.groovy job parameters:

- String: JDK_VERSION
JDK version to trigger. (Numerical version number, 8, 11, 17, ...)

- String: MIRROR_REPO
github repository where source mirror is located for the given JDK_VERSION

- String: BINARIES_REPO
github organisation/repo template for where binaries are published for jdk-NN, "_NN_" gets replaced by the version

- CheckBox: FORCE_MAIN
Force the trigger of the "main" pipeline build for the current latest build tag, even if it is already published

- CheckBox: FORCE_EVALUATION
Force the trigger of the "evaluation" pipeline build for the current latest build tag, even if it is already published

- Multi-line Text: OVERRIDE_MAIN_TARGET_CONFIGURATIONS
Override targetConfigurations for FORCE_MAIN, eg: { "x64Linux": [ "temurin" ], "x64Mac": [ "temurin" ] }

- Multi-line Text: OVERRIDE_EVALUATION_TARGET_CONFIGURATIONS
Override targetConfigurations for FORCE_EVALUATION, eg: { "aarch64AlpineLinux": [ "temurin" ] }


## Build status

Table generated with `generateBuildMatrix.sh`

<!-- markdownlint-disable -->

| Platform | Java 8 | Java 11 | Java 17 | Java 21 | Java HEAD |
|------|----|----|----|----|----|
| aix-ppc64-temurin | [![Build Status][i-r1c1]][j-r1c1] | [![Build Status][i-r1c2]][j-r1c2] | [![Build Status][i-r1c3]][j-r1c3] | [![Build Status][i-r1c4]][j-r1c4] | [![Build Status][i-r1c5]][j-r1c5] | 
| alpine-linux-aarch64-temurin | [![Build Status][i-r2c1]][j-r2c1] | [![Build Status][i-r2c2]][j-r2c2] | [![Build Status][i-r2c3]][j-r2c3] | N/A | [![Build Status][i-r2c5]][j-r2c5] | 
| alpine-linux-x64-temurin | [![Build Status][i-r3c1]][j-r3c1] | [![Build Status][i-r3c2]][j-r3c2] | [![Build Status][i-r3c3]][j-r3c3] | [![Build Status][i-r3c4]][j-r3c4] | [![Build Status][i-r3c5]][j-r3c5] | 
| linux-aarch64-temurin | [![Build Status][i-r4c1]][j-r4c1] | [![Build Status][i-r4c2]][j-r4c2] | [![Build Status][i-r4c3]][j-r4c3] | [![Build Status][i-r4c4]][j-r4c4] | [![Build Status][i-r4c5]][j-r4c5] | 
| linux-arm-temurin | [![Build Status][i-r5c1]][j-r5c1] | [![Build Status][i-r5c2]][j-r5c2] | [![Build Status][i-r5c3]][j-r5c3] | [![Build Status][i-r5c4]][j-r5c4] | [![Build Status][i-r5c5]][j-r5c5] | 
| linux-ppc64le-temurin | [![Build Status][i-r6c1]][j-r6c1] | [![Build Status][i-r6c2]][j-r6c2] | [![Build Status][i-r6c3]][j-r6c3] | [![Build Status][i-r6c4]][j-r6c4] | [![Build Status][i-r6c5]][j-r6c5] | 
| linux-riscv64-temurin | N/A | N/A | N/A | N/A | [![Build Status][i-r7c5]][j-r7c5] | 
| linux-riscv64-temurin-cross | N/A | N/A | N/A | N/A | [![Build Status][i-r8c5]][j-r8c5] | 
| linux-s390x-temurin | [![Build Status][i-r9c1]][j-r9c1] | [![Build Status][i-r9c2]][j-r9c2] | [![Build Status][i-r9c3]][j-r9c3] | [![Build Status][i-r9c4]][j-r9c4] | [![Build Status][i-r9c5]][j-r9c5] | 
| linux-x64-temurin | [![Build Status][i-r10c1]][j-r10c1] | [![Build Status][i-r10c2]][j-r10c2] | [![Build Status][i-r10c3]][j-r10c3] | [![Build Status][i-r10c4]][j-r10c4] | [![Build Status][i-r10c5]][j-r10c5] | 
| mac-aarch64-temurin | N/A | [![Build Status][i-r11c2]][j-r11c2] | [![Build Status][i-r11c3]][j-r11c3] | [![Build Status][i-r11c4]][j-r11c4] | [![Build Status][i-r11c5]][j-r11c5] | 
| mac-x64-temurin | [![Build Status][i-r12c1]][j-r12c1] | [![Build Status][i-r12c2]][j-r12c2] | [![Build Status][i-r12c3]][j-r12c3] | [![Build Status][i-r12c4]][j-r12c4] | [![Build Status][i-r12c5]][j-r12c5] | 
| solaris-sparcv9-temurin | [![Build Status][i-r13c1]][j-r13c1] | N/A | N/A | N/A | N/A | 
| solaris-x64-temurin | [![Build Status][i-r14c1]][j-r14c1] | N/A | N/A | N/A | N/A | 
| windows-aarch64-temurin | N/A | [![Build Status][i-r15c2]][j-r15c2] | [![Build Status][i-r15c3]][j-r15c3] | N/A | [![Build Status][i-r15c5]][j-r15c5] | 
| windows-x64-temurin | [![Build Status][i-r16c1]][j-r16c1] | [![Build Status][i-r16c2]][j-r16c2] | [![Build Status][i-r16c3]][j-r16c3] | [![Build Status][i-r16c4]][j-r16c4] | [![Build Status][i-r16c5]][j-r16c5] | 
| windows-x64-temurin_reproduce_compare | N/A | N/A | N/A | N/A | N/A | 
| windows-x86-32-temurin | [![Build Status][i-r18c1]][j-r18c1] | [![Build Status][i-r18c2]][j-r18c2] | [![Build Status][i-r18c3]][j-r18c3] | N/A | N/A | 

[i-r1c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-aix-ppc64-temurin
[j-r1c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-aix-ppc64-temurin
[i-r1c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-aix-ppc64-temurin
[j-r1c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-aix-ppc64-temurin
[i-r1c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-aix-ppc64-temurin
[j-r1c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-aix-ppc64-temurin
[i-r1c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-aix-ppc64-temurin
[j-r1c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-aix-ppc64-temurin
[i-r1c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-aix-ppc64-temurin
[j-r1c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-aix-ppc64-temurin
[i-r2c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-alpine-linux-aarch64-temurin
[j-r2c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-alpine-linux-aarch64-temurin
[i-r2c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-alpine-linux-aarch64-temurin
[j-r2c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-alpine-linux-aarch64-temurin
[i-r2c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-alpine-linux-aarch64-temurin
[j-r2c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-alpine-linux-aarch64-temurin
[i-r2c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-alpine-linux-aarch64-temurin
[j-r2c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-alpine-linux-aarch64-temurin
[i-r3c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-alpine-linux-x64-temurin
[j-r3c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-alpine-linux-x64-temurin
[i-r3c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-alpine-linux-x64-temurin
[j-r3c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-alpine-linux-x64-temurin
[i-r3c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-alpine-linux-x64-temurin
[j-r3c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-alpine-linux-x64-temurin
[i-r3c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-alpine-linux-x64-temurin
[j-r3c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-alpine-linux-x64-temurin
[i-r3c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-alpine-linux-x64-temurin
[j-r3c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-alpine-linux-x64-temurin
[i-r4c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-aarch64-temurin
[j-r4c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-aarch64-temurin
[i-r4c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-temurin
[j-r4c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-temurin
[i-r4c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-aarch64-temurin
[j-r4c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-aarch64-temurin
[i-r4c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-linux-aarch64-temurin
[j-r4c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-linux-aarch64-temurin
[i-r4c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-aarch64-temurin
[j-r4c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-aarch64-temurin
[i-r5c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-arm-temurin
[j-r5c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-arm-temurin
[i-r5c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-arm-temurin
[j-r5c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-arm-temurin
[i-r5c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-arm-temurin
[j-r5c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-arm-temurin
[i-r5c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-linux-arm-temurin
[j-r5c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-linux-arm-temurin
[i-r5c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-arm-temurin
[j-r5c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-arm-temurin
[i-r6c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-ppc64le-temurin
[j-r6c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-ppc64le-temurin
[i-r6c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-ppc64le-temurin
[j-r6c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-ppc64le-temurin
[i-r6c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-ppc64le-temurin
[j-r6c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-ppc64le-temurin
[i-r6c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-linux-ppc64le-temurin
[j-r6c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-linux-ppc64le-temurin
[i-r6c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-ppc64le-temurin
[j-r6c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-ppc64le-temurin
[i-r7c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-riscv64-temurin
[j-r7c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-riscv64-temurin
[i-r8c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-riscv64-temurin-cross
[j-r8c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-riscv64-temurin-cross
[i-r9c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-s390x-temurin
[j-r9c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-s390x-temurin
[i-r9c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-s390x-temurin
[j-r9c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-s390x-temurin
[i-r9c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-s390x-temurin
[j-r9c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-s390x-temurin
[i-r9c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-linux-s390x-temurin
[j-r9c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-linux-s390x-temurin
[i-r9c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-s390x-temurin
[j-r9c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-s390x-temurin
[i-r10c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-temurin
[j-r10c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-temurin
[i-r10c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-temurin
[j-r10c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-temurin
[i-r10c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-linux-x64-temurin
[j-r10c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-linux-x64-temurin
[i-r10c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-linux-x64-temurin
[j-r10c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-linux-x64-temurin
[i-r10c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-x64-temurin
[j-r10c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-x64-temurin
[i-r11c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-aarch64-temurin
[j-r11c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-aarch64-temurin
[i-r11c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-mac-aarch64-temurin
[j-r11c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-mac-aarch64-temurin
[i-r11c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-mac-aarch64-temurin
[j-r11c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-mac-aarch64-temurin
[i-r11c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-aarch64-temurin
[j-r11c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-aarch64-temurin
[i-r12c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-temurin
[j-r12c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-temurin
[i-r12c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-x64-temurin
[j-r12c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-x64-temurin
[i-r12c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-mac-x64-temurin
[j-r12c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-mac-x64-temurin
[i-r12c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-mac-x64-temurin
[j-r12c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-mac-x64-temurin
[i-r12c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-x64-temurin
[j-r12c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-x64-temurin
[i-r13c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-sparcv9-temurin
[j-r13c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-sparcv9-temurin
[i-r14c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-x64-temurin
[j-r14c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-x64-temurin
[i-r15c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-aarch64-temurin
[j-r15c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-aarch64-temurin
[i-r15c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-aarch64-temurin
[j-r15c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-aarch64-temurin
[i-r15c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-aarch64-temurin
[j-r15c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-aarch64-temurin
[i-r16c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-temurin
[j-r16c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-temurin
[i-r16c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-temurin
[j-r16c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-temurin
[i-r16c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-x64-temurin
[j-r16c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-x64-temurin
[i-r16c4]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk21/jdk21-windows-x64-temurin
[j-r16c4]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21/job/jdk21-windows-x64-temurin
[i-r16c5]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x64-temurin
[j-r16c5]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x64-temurin
[i-r18c1]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x86-32-temurin
[j-r18c1]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x86-32-temurin
[i-r18c2]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x86-32-temurin
[j-r18c2]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x86-32-temurin
[i-r18c3]: https://ci.adoptium.net/buildStatus/icon?job=build-scripts/jobs/jdk17u/jdk17u-windows-x86-32-temurin
[j-r18c3]: https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk17u/job/jdk17u-windows-x86-32-temurin

<!-- markdownlint-enable -->
