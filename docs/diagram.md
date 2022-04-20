# Diagram

## High level diagram on Jenkins Job Creation

```mermaid
flowchart  LR  
Trigger[SCM change] --> Seed[build-scripts/utils/build-pipeline-generator]
Seed --create jobs--> Pipelinen[build-scripts/openjdk*ver-pipeline]
Seed --create jobs--> Pipelinew[build-scripts/weekly-openjdk*ver-pipeline]
```

```mermaid
flowchart  LR
Seed[Seed Job: build-pipeline-generator] --call--> Call1[Script: build/regeneration/build_pipeline_generator.groovy]
Seed[Seed Job: build-pipeline-generator] --load--> Load1[Load Config: jobs/configurations/*.groovy]

Call1 --create--> Pipelinen[Nightly Job: build-scripts/openjdk*ver-pipeline]
Load1 --input--> Pipelinen

Call1 --create--> Pipelinew[Weekly Job: build-scripts/weekly-openjdk*ver-pipeline]
Load1 --input--> Pipelinew
```

```mermaid
flowchart  LR  
Trigger2[manual trigger] --> Seed2[build-scripts/utils/pipeline_jobs_generator_jdk*ver]
Seed2 --create--> Downstream1[Job: build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var]
Seed2 --create--> Downstream2[Job: build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var]
Seed2 --create--> Downstream3[Job: build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var]
Seed2 --create--> Downstream4[Job: build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var]
 ```

```mermaid
flowchart  LR
Seed2[Seed Job: pipeline_jobs_generator_jdk*ver] --call-->
Call2[Script: build/regeneration/build_job_generator.groovy]

Call2 --load--> Load1[Build Config: jobs/configurations/jdk*ver_pipeline_config.groovy] --loop--> DSL[jobDsl: common/create_job_from_template.groovy]
Call2 --load--> Load2[Target Config: jobs/configurations/jdk*ver.groovy] --> DSL
Call2 --call--> Load3[Script: common/config_regeneration.groovy] --creatJob--> DSL

DSL --create--> DS1[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant]
DSL --create--> DS2[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant]
DSL --create--> DS3[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant]
DSL --create--> DS4[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant]
```

## High level diagram on Jenkins build/test jobs interaction

```mermaid
flowchart  LR  
WeeklyTimer --run job--> PipelineW[build-scripts/weekly-openjdk*ver-pipeline]
PipelineW --multiple trigger by variants as Release releaseType--> PipelineN[build-scripts/openjdk*ver-pipeline]
NightlyTimer --rrun job with Nightly releaseType--> PipelineN

PipelineN --run downstream--> DS1[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant>]
PipelineN --run downstream--> DS2[build-scripts/jobs/jdk*ver/jdk*ver-*os-*rch-*variant>]
PipelineN --run downstream --> DS3[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant>]
PipelineN --run downstream --> DS12[build-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant>]
```

## Mainflow logic of creation pipeline: openjdk\*ver-pipeline

```mermaid
flowchart TD 
Call[build/openjdk_pipeline.groovy] --load script--> 
Load1[build/common/build_base_file.groovy]
 
Call --load config--> Load2[build/jobs/configurations/jdk*ver_pipeline_config.groovy]

Call --sharedlib --> 
Load3[Git repo: openjdk-jenkins-helper]

Load1 --call_function--> Build[doBuild]
Load2 --input--> Build
Load3 --input--> Build

Build --create_downstream_jobs --> 
Done[Job: jdk*ver/job/jdk*ver-*os-*arch-*variant]
```

## Mainflow logic of creation job: "build-scripts/job/utils/job/pipeline_jobs_generator_jdk*ver"

```mermaid
flowchart TD 
build/regeneration/build_job_generator.groovy --load--> build/common/import_lib.groovy --load--> jobs/configurations/jdk*_pipeline_config.groovy --load--> jobs/configurations/jdk*.groovy --load--> common/config_regeneration.groovy --call_function--> regenerate --call_function--> makeJob --call--> createJob --> DSL[jobDSL: common/create_job_from_template.groovy] --set--> scm[Git clone: ci-jenkins-pipelines] --set--> properties[Permission/logRotator/...]--> parameters
```

## Mainflow logic of build job: jobs/jdk\*ver/jdk\*ver-\*os-\*arch-\*variant

```mermaid
flowchart TD
starter[kick_off_build.groovy] --load--> import[build/common/import_lib.groovy] --load--> Load1[build/common/openjdk_build_pipeline.groovy] --call_function--> Builder[build] 
Builder --sharedlib --> 
Load3[Git repo: openjdk-jenkins-helper] --> docker{build in docker}
docker --true: run--> dockerbuild[docker.build] --> sign
docker --false:call_function--> CallbuildScript[buildScripts] --> sign{enableSigner} --true:call--> sing[sign] --> testStage{enableTests}
sign{enableSigner} --false:skip --> testStage --true:call_function--> smoketest[runSmokeTests] --pass:run-->
smoke[Job: jobs/jdk*ver/jdk*ver-<os>-<arch>-<variant>_SmokeTests] --call_function--> Stage2[runAQATests]

Stage2 --run_job--> sanity1[sanity.openjdk]
Stage2 --run_job--> sanity2[sanity.system]
Stage2 --run_job--> sanity3[sanity.perf]
Stage2 --run_job--> sanity4[sanity.functional]
Stage2 --run_job--> extended1[extended.system]
Stage2 --run_job--> extended2[extended.functional]
Stage2 -.->|run weekly extra test job| weekly1[extended.openjdk]
Stage2 -.->|run weekly extra test job| weekly2[extended.perf]
Stage2 -.->|run weekly extra test job| weekly3[special.functional]

sanity1 --if:pass--> shouldInstaller
sanity2 --if:pass--> shouldInstaller
sanity3 --if:pass--> shouldInstaller
sanity4 --if:pass--> shouldInstaller
extended1 --if:pass--> shouldInstaller
extended2 --if:pass--> shouldInstaller
weekly1 -->|if:pass| shouldInstaller
weekly2  -->|if:pass| shouldInstaller
weekly3  -->|if:pass| shouldInstaller

shouldInstaller --> install{enableInstaller} --true:call_function--> bI[buildInstaller] --call_function--> sI[signInstaller]
```

## Breakdown logic of build script: openjdk_build_pipeline.groovy

```mermaid

flowchart TD
CallbuildScript[function: buildScripts] --git_clone--> checkout["Repo: temurin-build"] -->

checkmac{check: Mac && !jdk8u} --true--> flagmac["Call:jmods\nSet flag: --make-exploded-image\n"] --call_script -->
scriptmake[sh:script build-farm/make-adopt-build-farm.sh] --> meta["Call function:\nwriteMetadata"] --> arch["Jenkins step:\narchiveArtifacts"] --> clean2["Cleanup workspace"]
checkmac --false:call_script--> scriptmake

bk1[Script: build-farm/make-adopt-build-farm.sh] --source--> step1[sbin/common/constants.sh] --source -->step[build-farm/set-platform-specific-configurations.sh] --call_script--> step3[makejdk-any-platform.sh]

bk1.2[makejdk-any-platform.sh] --> source[various shell scripts] --> f1["Call functions:\nconfigure_build\nwriteConfigToFile"] -->
isD{USE_DOCKER} --true:run_function from docker-build.sh--> f3[buildOpenJDKViaDocker] -->dockerarg[set entrypoint to /openjdk/sbin/build.sh] --> cmd["Run: docker build"]
isD --false:run_function from native-build.sh--> f4[buildOpenJDKInNativeEnvironment] --call_script--> sbin/build.sh

bk1.3[sbin/build.sh] --call_function from config_init.sh--> do1.3.1["Call function:\nloadConfigFromFile\nfixJavaHomeUnderDocker\nparseArguments"] -->

is1{"MacOS Logic\nCheck flag: --assemble-exploded-image\n(ASSEMBLE_EXPLODED_IMAGE)"} --true-->
do2.1["Build:\nbuildTemplatedFile\nexecuteTemplatedFile\naddInfoToReleaseFile\naddInfoToJson"] -->
is2{"Check flag: --create-sbom\n(CREATE_SBOM)"} --true--> do3.1["Call functions:\nbuildCyclonedxLib: ant build\ngenerateSBoM"] --post_build-->
post["Call functions:\nremovingUnnecessaryFiles\ncopyFreeFontForMacOS\nsetPlistForMacOS\naddNoticeFile\ncreateOpenJDKTarArchive"] --> finish[Done]

is1 --false--> step1.1.1["pre-build:\nwipeOutOldTargetDir\ncreateTargetDir\nconfigureWorkspace"] -->
BUILD["Build:\ngetOpenJDKUpdateAndBuildVersion\nconfigureCommandParameters\nbuildTemplatedFile\nexecuteTemplatedFile"] --post build-->

is3{"check flag: --make-exploded-image\n(MAKE_EXPLODED)"} --flase-->
step2.1.1["Call functions:\nprintJavaVersionString\naddInfoToReleaseFile\naddInfoToJson"] --> is2 

is2 --false--> post
is3 --true--> post

bk2[function: writeMetadata] --> finit{initialWrite} --false--> listArchives[Loop: installer files under target/*]

finit --true:check various files--> check1[target/metadata/*] --load--> check2["file: config/makejdk-any-platform.args"] --> listArchives

listArchives --run--> run[command: shasum on *file] --> write[output: *file.json]
```

## High level docker image build (adopt image)

```mermaid
flowchart TD
job[openjdk_build_docker_multiarch] --load--> load[Jenkinsfile] --stage1-->
stage1[Docker build stage]
stage1 --> 11[linux x64] --> stagecheck{ build all pass}
stage1 --> 12[linux aarch64]--> stagecheck
stage1 --> 13[linux armv7l]--> stagecheck
stage1 --> 14[linux ppc64le]--> stagecheck
stage1 --> 15[linux s390x]--> stagecheck
stagecheck --true--> stage2[Docker Manifest]
stagecheck --false--> abort[Abort job]
stage2 --> 21[manifest 8] --> finalcheck{ update manifest done}
stage2 --> 22[manifest 11] --> finalcheck
stage2 --> 23[manifest 15] --> finalcheck
stage2 --> 24[manifest 16] --> finalcheck
finalcheck --true--> done[Job finish]

Stage1[Docker build stage] --call_function --> build[dockerBuild]
Stage2[Docker Manifest] --call_function --> mani[dockerManifest]

build[dockerBuild] --> login1[login dockerhub] --git pull--> git1[openjdk-docker.git] --call--> ba[Script: build_all.sh]

mani[dockerManifest] --> login2[login dockerhub] --git pull--> git2[openjdk-docker.git] --call--> ua[Script: update_manifest_all.sh]

buildall[Script: build_all.sh] -->s1[handle .summary_table file] --> s2[load common_functions.sh] --> s3[clenaup .summary_table file] --> s4[cleanup env: container, image, menifests , temp scripts] --call_script--> s5[Script: build_latest] -->result{build failed or push_commands.sh non-exist} --false-->s6[Run: push_commands.sh] --test image-->s7[Run: test_multiarch.sh]

common[common_functions.sh] --> c1[supported_version:8,11,15,16]

updateall[Script: update_manifest_all.sh]

```
