*Note: The contents of this file was split out from the top level README.md
in this repository*

## Configuration Files used by the build job generators

The [pipelines/jobs/configurations](pipelines/jobs/configurations) directory contains two categories of configuration files that our jenkins pipelines use (Nicknamed [#Build Configs](#build) and [#Nightly Configs](#nightly) for short).

To ensure both configurations are not overridden in a race condition scenario by another job, the [job generators](../../build/regeneration/README.md) ensure they remain in the sync with the repository.

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

### Nightly

The nightly or beta/non-release config files are the ones that follow the format `jdkxx(u).groovy` with `xx` being the version number and an optional `u` if the Java source code is pulled from an update repository. Each is a simple groovy script that's contents can be [loaded in](https://www.jenkins.io/doc/pipeline/steps/workflow-cps/#load-evaluate-a-groovy-source-file-into-the-pipeline-script) and accessed by another script.

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

