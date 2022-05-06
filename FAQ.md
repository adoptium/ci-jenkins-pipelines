# ci-jenkins-pipelines FAQ

This document covers cover how to perform various repeatable tasks in the
repository that might not otherwise be obvious from just looking at the
repository.

## Access control in this repository

The GitHub teams relevant to this repository are as follows (Note, you
won't necessarily have access to see these links):

- [GetOpenJDK](https://github.com/orgs/AdoptOpenJDK/teams/getopenjdk) - `Triage` level of access which lets you assign issues to people
- [build](https://github.com/orgs/AdoptOpenJDK/teams/build) - `Write` access which lets you approve and merge PRs and run and configure most Jenkins jobs
- [release](https://github.com/orgs/AdoptOpenJDK/teams/build) - Allows you to run the release jobs in Jenkins

## How do I find my way around Eclipse Adoptium's build automation scripts?

I wrote this diagram partially for my own benefit in [issue 957](https://github.com/adoptium/temurin-build/issues/957) that lists the Jenkins jobs (`J`) and Groovy scripts from GitHub (`G`).
I think it would be useful to incorporate this into the documentation (potentially annotated with a bit more info) so people can find their way around the myriad of script levels that we now have.

Note that the "end-user" scripts start at `makejdk-any-platform.sh` and a
diagram of those relationships can be seen [here](https://github.com/adoptium/ci-jenkins-pipelines/blob/master/docs/images/AdoptOpenJDK_Build_Script_Relationships.png)

```markdown
J - build-scripts/job/utils/job/build-pipeline-generator
G   - Create openjdk*-pipeline jobs from pipelines/jobs/pipeline_job_template.groovy
J   - openjdk11-pipeline
G     - pipelines/build/openjdk_pipeline.groovy
G       - pipelines/build/common/build_base_file.groovy
G         - create_job_from_template.groovy (Generates e.g. jdk11u-linux-x64-hotspot)
G       - configureBuild()
G         - .doBuild() (common/build_base_file.groovy)
J           - context.build job: downstreamJobName (e.g. jdk11u/job/jdk11u-linux-x64-hotspot)
J             (Provides JAVA_TO_BUILD, ARCHITECTURE, VARIANT, TARGET_OS + tests)
G             - openjdk_build_pipeline.groovy
G               - context.sh make-adopt-build-farm.sh
```

*See the [temurin-build FAQ.md](https://github.com/adoptium/temurin-build/blob/master/FAQ.md#how-do-i-find-my-way-around-adoptopenjdks-build-automation-scripts) for the shell script side of the pipeline*

## How do I build more quickly?

There are a couple of options that are enabled by default in the pipelines
but slow down the build. If you're just looking for a "quick" build to test
something then you can skip the custom cacerts generation and the creation
of debug images as follows - it will still produce a usable JDK with these
options:

- additionalConfigureArgs `--with-native-debug-symbols=none`
- additionalBuildArgs `--custom-cacerts false`

## Adding a new major release to be built

1. Create the new release repository under GitHub.com/adoptium (generally `openjdk-jdkxx`)
2. Add the release to the list at [pipeline file](/pipelines/build)
3. Adjust the PR testing pipeline [Example](https://github.com/adoptium/temurin-build/pull/1394) to use the new release

## Removing a major release once you've added a new one

Unless the last release was an LTS one, you will generally want to remove one of the old versions after creating a new one. This can be done with `disableJob = true` in the release configuration files

[Example](https://github.com/adoptium/temurin-build/pull/1303/files)

## How to enable/disable a particular build configuration

1. Add/Remove it from the [configuration files](pipelines/jobs/configurations)
2. if you're removing one and it's not just temporarily, you may want to delete the specific job from Jenkins too

[Example PR - removing aarch64 OpenJ9 builds](https://github.com/adoptium/temurin-build/pull/1452)

## How to add a new build variant

We perform different builds such as the based openjdk (hotspot), builds from the Eclipse OpenJ9 codebase as well as others such as Corretto and SAPMachine. These alternatives are referred to as build variants.

First you will need to add support into the [pipeline files](pipelines/build) as well as any environment-specific changes you need to make in the [platform files](https://github.com/adoptium/temurin-build/tree/master/build-farm/platform-specific-configurations)
For an example, see [this PR where Dragonwell was added](https://github.com/adoptium/temurin-build/pull/2051/files)
For more information on other changes required, see [this document](https://github.com/AdoptOpenJDK/TSC/wiki/Adding-a-new-build-variant)

## I've modified the build scripts - how can I test my changes?

If you're making changes ensure you follow the contribution guidelines in
[CONTRIBUTING.md](CONTRIBUTING.md).

In order to test whether your changes work use the [test-build-script-pull-request](https://ci.adoptopenjdk.net/job/build-scripts-pr-tester/job/test-build-script-pull-request/) job!
Pass it your fork name (e.g. `https://github.com/sxa555/openjdk-build`) and the name of the branch and it will run a build using your updated scripts.
For more information, see the [PR testing documentation](pipelines/build/prTester/README.md).

## I want to use my own configuration files or scripts on my own Jenkins instance. How do I do it?

Check out [Adopt's guide](docs/UsingOurScripts.md) to setting up your own scripts and configurations (while not having to keep up with Adopt's changes)!

## I want to build code from my own fork/branch of openjdk in jenkins

You will need to add some parameters to the `BUILD_ARGS` on the individual
platform-specific pipeline (or `additionalBuildArgs` if runnibg a top level pipeline) and
specify `--disable-adopt-branch-safety` for example:

`--disable-adopt-branch-safety -r https://github.com/sxa/openjdk-jdk11u -b mybranch`
