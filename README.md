# Jenkins pipeline files for building OpenJDK

AdoptOpenJDK makes use of these scripts to build binaries on the build farm at <https://ci.adoptopenjdk.net>

## Repository contents

This repository contains several useful scripts in order to build OpenJDK
personally or at build farm scale.

1. The `docs` folder contains images and utility scripts to produce up to date
documentation.
1. The `pipelines` folder contains the Groovy pipeline scripts for Jenkins
(e.g. build | test | checksum | release).

## Metadata

Alongside the built assets a metadata file will be created with info about the build. This will be a JSON document of the form:

```json
{
    "vendor": "AdoptOpenJDK",
    "os": "mac",
    "arch": "x64",
    "variant": "openj9",
    "variant_version": {
        "major": "0",
        "minor": "22",
        "security": "0",
        "tags": "m2"
    },
    "version": {
        "minor": 0,
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
    "configure_arguments": "<output of bash configure>"
}
```

The Metadata class is contained in the [Metadata.groovy](https://github.com/AdoptOpenJDK/ci-jenkins-pipelines/blob/master/pipelines/library/src/common/MetaData.groovy) file and the Json is constructed and written in the [openjdk_build_pipeline.groovy](https://github.com/AdoptOpenJDK/ci-jenkins-pipelines/blob/master/pipelines/build/common/openjdk_build_pipeline.groovy) file.

It is worth noting the additional tags on the semver is the adopt build number.

Below are all of the keys contained in the metadata file and some example values that can be present.

----

- `vendor:`
Example values: [`AdoptOpenJDK`, `Alibaba`]

This tag is used to identify the vendor of the JDK being built, this value is set in the [build.sh](https://github.com/AdoptOpenJDK/openjdk-build/blob/805e76acbb8a994abc1fb4b7d582486d48117ee8/sbin/build.sh#L183) file and defaults to "AdoptOpenJDK".

----

- `os:`
Example values: [`windows`, `mac`, `linux`, `aix`, `solaris`]

This tag identifies the operating system the JDK has been built on (and should be used on).

----

- `arch:`
Example values: [`aarch64`, `ppc64`, `s390x`, `x64`, `x86-32`, `arm`]

This tag identifies the architecture the JDK has been built on and it intended to run on.

----

- `variant:`
Example values: [`hotspot`, `openj9`, `corretto`, `dragonwell`]

This tag identifies the JVM being used by the JDK, "dragonwell" itself is not a JVM but is currently considered a variant in its own right.

----

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

----

- `version:`

This tag contains the full version information of the JDK built, it uses the [VersionInfo.groovy](https://github.com/AdoptOpenJDK/ci-jenkins-pipelines/blob/master/pipelines/library/src/common/VersionInfo.groovy) class and the [ParseVersion.groovy](https://github.com/AdoptOpenJDK/ci-jenkins-pipelines/blob/master/pipelines/library/src/ParseVersion.groovy) class.

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
Formed from the major, minor, security, and build number by the [formSemver()](https://github.com/AdoptOpenJDK/ci-jenkins-pipelines/blob/805e76acbb8a994abc1fb4b7d582486d48117ee8/pipelines/library/src/common/VersionInfo.groovy#L123) function.

- `build:`
Example values: [`6`, `9`, `18`]  
The OpenJDK build number for the JDK being built.

- `opt:`
Example values: [`202008210941`, `202010120348`, `202007272039`]

----

- `scmRef:`
Example values: [`dragonwell-8.4.4_jdk8u262-b10`, `jdk-16+19_adopt-61198-g59e3baa94ac`, `jdk-11.0.9+10_adopt-197-g11f44f68c5`, `23f997ca1`]  

A reference the the base JDK repository being build, usually including a Github commit reference, i.e. `jdk-16+19_adopt-61198-g59e3baa94ac` links to <https://github.com/AdoptOpenJDK/openjdk-jdk/commit/59e3baa94ac> via the commit SHA **59e3baa94ac**.

Values that only contain a commit reference such as `23f997ca1` are OpenJ9 commits on their respective JDK repositories, for example **23f997ca1** links to the commit <https://github.com/ibmruntimes/openj9-openjdk-jdk14/commit/23f997ca1>.

----

- `buildRef:`
Example values: [`openjdk-build/fe0f2dba`, `openjdk-build/f412a523`]  
A reference to the build tools repository used to create the JDK, uses the format **repository-name**/**commit-SHA**.

----

- `version_data:`
Example values: [`jdk8u`, `jdk11u`, `jdk14u`, `jdk`]

----

- `binary_type:`
Example values: [`jdk`, `jre`, `debugimage`, `testimage`]

----

- `sha256:`
Example values: [`20278aa9459e7636f6237e85fcd68deec1f42fa90c6c541a2dfa127f4156d3e2`, `2f9700bd75a807614d6d525fbd8d016c609a9ea71bf1ffd5d4839f3c1c8e4b8e`]  
A SHA to verify the contents of the JDK.

----

- `full_version_output:`
Example values:

```java
openjdk version \"1.8.0_252\"\nOpenJDK Runtime Environment (Alibaba Dragonwell 8.4.4) (build 1.8.0_252-202010111720-b06)\nOpenJDK 64-Bit Server VM (Alibaba Dragonwell 8.4.4) (build 25.252-b06, mixed mode)\n`
```

The full output of the command `java -version` for the JDK.

----

- `configure_arguments:`  
The full output generated by `configure.sh` for the JDK built.

## Build status

Table generated with `generateBuildMatrix.sh`
<!-- markdownlint-disable --> 
| Platform                  | Java 8 | Java 11| Java 15 | Java 16 | Java HEAD |
| ------------------------- | ------ | ------ | ------- | ------- | --------- |
| aix-ppc64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-aix-ppc64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-aix-ppc64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-aix-ppc64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-aix-ppc64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-aix-ppc64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-aix-ppc64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-aix-ppc64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-aix-ppc64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-aix-ppc64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-aix-ppc64-hotspot) | 
| aix-ppc64-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-aix-ppc64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-aix-ppc64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-aix-ppc64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-aix-ppc64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-aix-ppc64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-aix-ppc64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-aix-ppc64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-aix-ppc64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-aix-ppc64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-aix-ppc64-openj9) | 
| alpine-linux-x64-hotspot | N/A | N/A | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-alpine-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-alpine-linux-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-alpine-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-alpine-linux-x64-hotspot) | 
| freebsd-x64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-freebsd-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-freebsd-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-freebsd-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-freebsd-x64-hotspot) | N/A | N/A | N/A | 
| linux-aarch64-dragonwell | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-aarch64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-aarch64-dragonwell) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-dragonwell) | N/A | N/A | N/A | 
| linux-aarch64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-aarch64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-aarch64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-aarch64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-aarch64-hotspot) | 
| linux-aarch64-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-aarch64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-aarch64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-aarch64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-aarch64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-aarch64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-aarch64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-aarch64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-aarch64-openj9) | 
| linux-aarch64-openj9-linuxXL | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-aarch64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-aarch64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-aarch64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-aarch64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-aarch64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-aarch64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-aarch64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-aarch64-openj9-linuxXL) | 
| linux-arm-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-arm-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-arm-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-arm-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-arm-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-arm-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-arm-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-arm-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-arm-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-arm-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-arm-hotspot) | 
| linux-ppc64le-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-ppc64le-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-ppc64le-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-ppc64le-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-ppc64le-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-ppc64le-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-ppc64le-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-ppc64le-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-ppc64le-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-ppc64le-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-ppc64le-hotspot) | 
| linux-ppc64le-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-ppc64le-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-ppc64le-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-ppc64le-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-ppc64le-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-ppc64le-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-ppc64le-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-ppc64le-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-ppc64le-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-ppc64le-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-ppc64le-openj9) | 
| linux-ppc64le-openj9-linuxXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-ppc64le-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-ppc64le-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-ppc64le-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-ppc64le-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-ppc64le-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-ppc64le-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-ppc64le-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-ppc64le-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-ppc64le-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-ppc64le-openj9-linuxXL) | 
| linux-riscv64-openj9 | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-riscv64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-riscv64-openj9) | N/A | N/A | N/A | 
| linux-s390x-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-s390x-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-s390x-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-s390x-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-s390x-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-s390x-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-s390x-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-s390x-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-s390x-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-s390x-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-s390x-hotspot) | 
| linux-s390x-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-s390x-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-s390x-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-s390x-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-s390x-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-s390x-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-s390x-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-s390x-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-s390x-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-s390x-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-s390x-openj9) | 
| linux-s390x-openj9-linuxXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-s390x-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-s390x-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-s390x-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-s390x-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-s390x-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-s390x-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-s390x-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-s390x-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-s390x-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-s390x-openj9-linuxXL) | 
| linux-x64-corretto | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-corretto)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-corretto) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-corretto)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-corretto) | N/A | N/A | N/A | 
| linux-x64-dragonwell | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-dragonwell) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-dragonwell) | N/A | N/A | N/A | 
| linux-x64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-x64-hotspot) | 
| linux-x64-hotspot-linuxXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-hotspot-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-hotspot-linuxXL) | N/A | N/A | N/A | N/A | 
| linux-x64-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-x64-openj9) | 
| linux-x64-openj9-linuxXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-linux-x64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-linux-x64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-linux-x64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-linux-x64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-linux-x64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-linux-x64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-linux-x64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-linux-x64-openj9-linuxXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-linux-x64-openj9-linuxXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-linux-x64-openj9-linuxXL) | 
| mac-x64-corretto | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-corretto)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-corretto) | N/A | N/A | N/A | N/A | 
| mac-x64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-mac-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-mac-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-mac-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-mac-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-x64-hotspot) | 
| mac-x64-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-mac-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-mac-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-mac-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-mac-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-x64-openj9) | 
| mac-x64-openj9-macosXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-mac-x64-openj9-macosXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-mac-x64-openj9-macosXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-mac-x64-openj9-macosXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-mac-x64-openj9-macosXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-mac-x64-openj9-macosXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-mac-x64-openj9-macosXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-mac-x64-openj9-macosXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-mac-x64-openj9-macosXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-mac-x64-openj9-macosXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-mac-x64-openj9-macosXL) | 
| solaris-sparcv9-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-sparcv9-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-sparcv9-hotspot) | N/A | N/A | N/A | N/A | 
| solaris-x64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-solaris-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-solaris-x64-hotspot) | N/A | N/A | N/A | N/A | 
| windows-aarch64-hotspot | N/A | N/A | N/A | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-windows-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-windows-aarch64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-aarch64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-aarch64-hotspot) | 
| windows-x64-dragonwell | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-dragonwell) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-dragonwell)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-dragonwell) | N/A | N/A | N/A | 
| windows-x64-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-windows-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-windows-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-windows-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-windows-x64-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x64-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x64-hotspot) | 
| windows-x64-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-windows-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-windows-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-windows-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-windows-x64-openj9) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x64-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x64-openj9) | 
| windows-x64-openj9-windowsXL | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x64-openj9-windowsXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x64-openj9-windowsXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x64-openj9-windowsXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x64-openj9-windowsXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-windows-x64-openj9-windowsXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-windows-x64-openj9-windowsXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-windows-x64-openj9-windowsXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-windows-x64-openj9-windowsXL) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x64-openj9-windowsXL)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x64-openj9-windowsXL) | 
| windows-x86-32-hotspot | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x86-32-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x86-32-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk11u/jdk11u-windows-x86-32-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk11u/job/jdk11u-windows-x86-32-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk15u/jdk15u-windows-x86-32-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk15u/job/jdk15u-windows-x86-32-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk16/jdk16-windows-x86-32-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk16/job/jdk16-windows-x86-32-hotspot) | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk/jdk-windows-x86-32-hotspot)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk/job/jdk-windows-x86-32-hotspot) | 
| windows-x86-32-openj9 | [![Build Status](https://ci.adoptopenjdk.net/buildStatus/icon?job=build-scripts/jobs/jdk8u/jdk8u-windows-x86-32-openj9)](https://ci.adoptopenjdk.net/job/build-scripts/job/jobs/job/jdk8u/job/jdk8u-windows-x86-32-openj9) | N/A | N/A | N/A | N/A | 
<!-- markdownlint-enable -->