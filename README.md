<!-- textlint-disable terminology -->
# Jenkins pipeline files for building OpenJDK

Eclipse Adoptium makes use of these scripts to build binaries on the build farm at <https://ci.adoptium.net>

## Repository contents

This repository contains several useful scripts in order to build OpenJDK
personally or at build farm scale via jenkins. For the Temurin project at
Adoptium, this is done with the jenkins instance at [ci.adoptium.net](https://ci.adoptium.net)

1. The `docs` folder contains images and utility scripts to produce up to date
documentation.
2. The `pipelines` folder contains the Groovy pipeline scripts for Jenkins
(e.g. build | test | checksum | release).
3. The `tools` folder contains `pipelines/` analysis scripts that deliever success/failure trends and build scripts for code-tool dependancies for the build and test process (e.g. asmtools | jcov | jtharness | jtreg | sigtest).

For those who prefer diagrams, there is an overview of the information below
including it's interactions with the scripts in other repositories in our
[docs/ARCHITECTURE.md] file and specific ones on the pipeline types in
[docs/diagram.md]. If you want to set up these pipelines in your own jenkins
instance, see
[this guide](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/docs/UsingOurScripts.md).

## Overview of pipeline types

The starting point on the jenkins instance from the perspective of the
overall build pipelines is the [build-scripts
folder](https://ci.adoptium.net/job/build-scripts/).  This contains the high
level pipelines which are used to run the different types of build.  In the
names in this document `XX` is the JDK version number e.g.  8, 17, 21 and so
on.  There is one of these for each JDK version which we support.

When talking about the different types of pipelines, the ones named
"*openjdkXX-pipeline" are referred to as the "top level versioned pipelines"
and the subjobs later on are the "platform specific pipelines"

### openjdkXX-pipeline

These were historically used for regular builds of each of our release
platforms using the current state of the master branch of the codebase -
which is it's default behaviour - but is now run each time there is a new
tag in the upstream openjdk codebase.  These are triggered by the
`betaTrigger_XXea` jobs in the
[build-scripts/utils](https://ci.adoptium.net/job/build-scripts/job/utils/)
folder.  Note that JDK8 for, which comes from a separate codebase and
therefore is tagged separately, is triggered via a separate
[betaTrigger_8ea_arm32Linux](https://ci.adoptium.net/job/build-scripts/job/utils/job/betaTrigger_8ea_arm32Linux/)
job.

The betaTrigger_XXea jobs use
[trigger_beta_build.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/common/trigger_beta_build.groovy)
to determine when to run a build. This contains a trap for the expected GA
release times to prevent triggering so that machine time is not used up
while we are performing release build and test cycles.

Once complete, the openjdkXX-pipelines which will, by default, invoke the
separate
([refactor_openjdk_release_tool](https://ci.adoptium.net/job/build-scripts/job/release/job/refactor_openjdk_release_tool/))
job which will publish them as an `ea-beta`-suffixed release in github  under e.g.
[temurin-21-binaries](https://github.com/adoptium/temurin21-binaries/releases?q=ea-beta&expanded=true}).

### release-openjdkXX-pipeline 

These are not publicly visible but are used to build the fully tested
production binaries on a quarterly basis.  Similar to the openjdkXX-pipeline
jobs these are automatically triggered by the releaseTrigger_jdkXX jobs in
[build-scripts/utils](https://ci.adoptium.net/job/build-scripts/job/utils/)
every time a new `-ga` suffixed tag is detected.

releaseTrigger_jdkXX runs once an hour between the 10th and 25th of release
months (Jan, Mar, Apr, Jul, Sep, Oct) and check for a new `-ga` tag.  It runs
[triggerReleasePipeline.sh](https://github.com/adoptium/mirror-scripts/blob/master/triggerReleasePipeline.sh)
(from the [mirror-scripts](https://github.com/adoptium/mirror-scripts/)
repository).  That script has a loop that checks 5 times with a ten minute
gap between them so that the overall trigger is checked every ten minutes
during the days when it is active based on checking for the "expected" tag
from [releasePlan.cfg](https://github.com/adoptium/mirror-scripts/blob/master/releasePlan.cfg)
using the readExpectedGATag function in
[common.sh](https://github.com/adoptium/mirror-scripts/blob/master/common.sh)

A couple of points to note on the release configurations:

- [jdk8u_release.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk8u_release.groovy),
  [jdk11u_release.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk11u_release.groovy)
  and [jdk17u_release.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk11u_release.groovy)
  do not automatically run the win32 (`x32Windows`) builds - they get
  triggered manually during a release cycle in order to prioritise the
  x64Windows builds during the release cycle on the machines.
- Similarly the [jdk8u_release.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/jobs/configurations/jdk8u_release.groovy)
  does not include arm32, since that is built from a separate codebase and
  is tagged separately so cannot generally be triggered alongside the main
  builds.

### evaluation-openjdkXX-pipeline

These are similar to the openjdkXX-pipeline jobs, and are triggered from the
same betaTrigger_XXea jobs.  The evaluation pipelines are for platforms
which the Adoptium team are looking to potentially release at some point,
but they are not yet reliable or sufficiently tested.
which are not in the release.

### weekly-openjdkXX-pipeline / weekly-evaluation-openjdkXX-pipeline

These are no longer used. These were triggered over the weekend with an extended set
of tests, but since the regular openjdkXX-pipeline jobs are now running
approximately once a week (the usual cadence of new tags appearing in the
upstream codebases) we are running the full AQA suite in those pipelines.
These were triggered by timer and then invoked the openjdkXX-pipeline jobs
with the appropriate parameters.

### trestle-openjdkXX-pipeline

Trestle is the name of the experimental project to allow upstream openjdk
committers to run pipelines on our infrastructure in order to test code
changes in openjdk on the full set of platforms which Temurin supports. They
are triggered on demand from a subset of authorized users.

### PR tester

In addition to the main pipelines we have "PR tester" jobs that are run on
PRs to the pipelines repository in order to ensure they do not have any
unintended side effects before they are merged.  These are triggered when
[specific comments from authorized users](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/prTester/README.md#usage)
are added into the PR. In that
folder in jenkins there are separate versions of all of the
openjdkXX-pipelines that can be used to run against PRs and will not
"pollute" the history of the main pipelines.

More documentation on the PR tester process can be found in
[the prTester documentation](pipelines/build/prTester).

## Subjobs of the top level versioned pipelines (i.e. "platform specific pipelines")

Each of the top level versioned pipelines described above invoke lower level
jobs to run the platform-specific builds.  The jenkins folders containing
these scripts for each of the above top level versioned pipelines are as
follows:

Top level versioned pipeline | Platform-specific pipeline folder (TODO: Name these!)
---|---
openjdkXX-pipeline | [jobs/jdkXX](https://ci.adoptium.net/job/build-scripts/job/jobs/)
evaluation-openjdkXX-pipeline | [jobs/evaluation/jdkXX](https://ci.adoptium.net/job/build-scripts/job/jobs/job/evaluation/) [†]
weekly-openjdkXX-pipeline | jobs/jdkXX (Shared with openjdkXX-pipeline)
release-openjdkXX-pipeline | [jobs/release/jobs/jdkXX](https://ci.adoptium.net/job/build-scripts/job/jobs/job/release/job/jobs/) (Restricted access)
PR testers | build-test/jobs/jdkXX

[†] - The release jobs here are restricted access.  The release folder here
should also not be confused with the build-scripts/release folder which
contains jobs used for the final publishing of the builds (early access or
GA) to github

_Note: jdkXX is generally related to the name of the upstream codebase, which
will often have a `u` suffix.  At the moment we have a separate set of jobs
for non-u and u versions when the upstream codebase changes.  TODO: Add note
about the new branch process for jdk23+_

Inside the jdkXX folders there are pipelines which perform a build of one
variant (e.g.  Temurin) for on JDK version on one platform, for example
[jdk21u-linux-aarch64-temurin](https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21u/job/jdk21u-linux-aarch64-temurin/)
which are reponsible for running the build using
[kick_off_build.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/common/kick_off_build.groovy)
and initiating the tests and other jobs against the completed build if
successful.  A "Smoke Test" job such as
[jdk21u-linux-aarch64-temurin-SmokeTests](https://ci.adoptium.net/job/build-scripts/job/jobs/job/jdk21u/job/jdk21u-linux-aarch64-temurin_SmokeTests/)
which is similar to our test jobs but runs the tests from the temurin-build
[buildAndPackage directory](https://github.com/adoptium/temurin-build/tree/master/test/functional/buildAndPackage)
which is initiated after the
build perfoms some basic tests against the build artefacts and acts as a
gate to kicking off the subsequent steps.  Once complete, the
openjdkXX-pipelines which run the early access builds will generally invoke
the jobs to publish them as a release in github (e.g.
[temurin-21-binaries](https://github.com/adoptium/temurin21-binaries/releases?q=ea-beta&expanded=true}).

## Job generation

As you can see from the above sections, there are a lot of separate jobs in
jenkins which are used during the build process.  Since there are so many of
them, these are not created manually, but are autogenerated using an
automatic generator process.

The top level
[build-pipeline-generator](https://ci.adoptium.net/job/build-scripts/job/utils/job/build-pipeline-generator/)
job uses
[build_pipeline_generator.groovy](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/pipelines/build/regeneration/build_pipeline_generator.groovy)
to generate the pipelines.  It will generate the top level versioned
openjdkXX-pipeline jobs.  Similarly there are pipeline_jobs_generator_jdkXX
jobs which use
[build_job_generator.groovy](pipelines/build/regeneration/build_job_generator.groovy)
to generate the subjobs for each platform/variant combination.  Both of
these pipelines are triggered on a change (PR merge) to the
ci-jenkins-pipelines repository. They will pause themselves if a pipeline is
not running so as to avoid interfering with a currently executing pipeline.
T
Similarly there is an evaluation-pipeline-generator and
evaluation-pipeline_jobs_generator_jdkXX for generating the evaluation jobs,
a trestle-pipeline-generator for those jobs, plus release-pipeline-generator
andand release_pipeline_jobs_generator_jdkXX for release jobs (the release
generators are not triggered automatically but are re-run manually at
certain points during each release cycle

The following is a brief summary of how the generation jobs work but more
details can be found in the
[generator documentation](pipelines/build/regeneration/README.md)

The generators make use of the following files in
[pipelines/jobs/configurations](pipelines/jobs/configurations). The README
file in that directory contains more details of the configuration format:

- The `jdkXX.groovy`, `jdkXX_evaluation.groovy`, `jdkXX_release.groovy` to determine which platforms to configure and generate for each version.
- The individual platform configurations, such as jenkins labels, are defined by `jdkXX_pipeline_config.groovy` files.

For more details on the regeneration process overall see the
[regeneration documentation](pipelines/build/regeneration/README.md)

## Metadata files generated with each build

<details>
<summary>Information about the metadata file generated alongside the build</summary>

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

- String: JDK_VERSION - JDK version to trigger. (Numerical version number, 8, 11, 17, ...)

- String: MIRROR_REPO - github repository where source mirror is located for the given JDK_VERSION

- String: BINARIES_REPO - github organisation/repo template for where binaries are published for jdk-NN, "\_NN\_" gets replaced by the version

- CheckBox: FORCE_MAIN - Force the trigger of the "main" pipeline build for the current latest build tag, even if it is already published

- CheckBox: FORCE_EVALUATION - Force the trigger of the "evaluation" pipeline build for the current latest build tag, even if it is already published

- Multi-line Text: OVERRIDE_MAIN_TARGET_CONFIGURATIONS - Override targetConfigurations for FORCE_MAIN, eg: { "x64Linux": [ "temurin" ], "x64Mac": [ "temurin" ] }

- Multi-line Text: OVERRIDE_EVALUATION_TARGET_CONFIGURATIONS - Override targetConfigurations for FORCE_EVALUATION, eg: { "aarch64AlpineLinux": [ "temurin" ] }

</details>

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
