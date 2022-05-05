# Diagram

## High level diagram on Jenkins build/test jobs interaction

```mermaid

flowchart TD

WeeklyTimer --run job--> PipelineW["Weekly Job:\nbuild-scripts/weekly-openjdk*ver-pipeline"]
PipelineW --multiple trigger by variants as Release releaseType--> PipelineN["Nightly Job:\nbuild-scripts/openjdk*ver-pipeline"]

NightlyTimer --run job with Nightly as releaseType--> PipelineN

PipelineN --run downstream--> DS1["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
PipelineN --run downstream--> DS2["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
PipelineN --run downstream --> DS3["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
PipelineN --run downstream --> DS12["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]

classDef yellow fill:#ffff99,stroke:#333,stroke-width:2px
classDef green fill:#66ff66,stroke:#333,stroke-width:2px
classDef c1 fill:#ccffff,stroke:#333,stroke-width:2px
classDef c2 fill:#99ffff,stroke:#333,stroke-width:2px
classDef c3 fill:#66ffff,stroke:#333,stroke-width:2px
classDef c12 fill:#33ffff,stroke:#333,stroke-width:2px
class PipelineW green
class PipelineN yellow
class DS1 c1
class DS2 c2
class DS3 c3
class DS12 c12
```

## High level diagram on Jenkins Job Creation

```mermaid
flowchart  LR

subgraph 1

Trigger[SCM change] --trigger--> Seed1["Seed Job:\nbuild-scripts/utils/build-pipeline-generator"]
Seed1 --create jobs--> Pipelinen1["Nightly Job:\nbuild-scripts/openjdk*ver-pipeline"]
Seed1 --create jobs--> Pipelinew1["Weekly Job:\nbuild-scripts/weekly-openjdk*ver-pipeline"]

end

subgraph 2

Seed["Seed Job:\nbuild-scripts/utils/build-pipeline-generator"] --call--> Call1["Script: build/regeneration/build_pipeline_generator.groovy"]
Seed--load--> Load1["Load Config: jobs/configurations/*.groovy"]
Call1 & Load1--> Pipelinew["Weekly Job: build-scripts/weekly-openjdk*ver-pipeline"] & Pipelinen["Nightly Job: build-scripts/openjdk*ver-pipeline"]

end

classDef red fill:#ff6666,stroke:#333,stroke-width:2px
classDef yellow fill:#ffff99,stroke:#333,stroke-width:2px
classDef green fill:#66ff66,stroke:#333,stroke-width:2px
class Seed1,Seed red
class Pipelinen1,Pipelinen yellow
class Pipelinew1,Pipelinew green

```

```mermaid

flowchart LR

subgraph 1

Trigger2[manual trigger] --> Seed["Seed Job:\nbuild-scripts/utils/pipeline_jobs_generator_jdk*ver"]

Seed --create--> Downstream1["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var"]
Seed --create--> Downstream2["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var"]
Seed --create--> Downstream3["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var"]
Seed --create--> Downstream4["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*var"]

end

subgraph 2

Seed2["Seed Job:\nbuild-scripts/utils/pipeline_jobs_generator_jdk*ver"] --"interal call"-->

Call2[Script: build/regeneration/build_job_generator.groovy]
Call2 --load--> Load1["Build Config:\njobs/configurations/jdk*ver_pipeline_config.groovy"] --loop--> DSL["jobDsl: common/create_job_from_template.groovy"]
Call2 --load--> Load2["Target Config:\njobs/configurations/jdk*ver.groovy"] --> DSL
Call2 --call--> Load3["Script: common/config_regeneration.groovy"] --"create job"--> DSL
DSL --create--> DS1["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
DSL --create--> DS2["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
DSL --create--> DS3["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]
DSL --create--> DS12["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]

end

subgraph 3

build/regeneration/build_job_generator.groovy --load-->
files["build/common/import_lib.groovy\njobs/configurations/jdk*_pipeline_config.groovy\njobs/configurations/jdk*.groovy\ncommon/config_regeneration.groovy"] --call_function--> f1["regenerate()"] --call_function--> f2["makeJob()"] --call_function--> f3["createJob()"] --> DSL2[jobDSL: common/create_job_from_template.groovy] --clone--> scm["git clone: ci-jenkins-pipelines"] --set--> properties["parameters:\nPermission\nlogRotator\n..."]

end

classDef green fill:#CCFFCC,stroke:#333,stroke-width:2px
classDef c1 fill:#ccffff,stroke:#333,stroke-width:2px
classDef c2 fill:#99ffff,stroke:#333,stroke-width:2px
classDef c3 fill:#66ffff,stroke:#333,stroke-width:2px
classDef c12 fill:#33ffff,stroke:#333,stroke-width:2px
class Seed2,Seed,3 green
class DS1,Downstream1 c1
class DS2,Downstream2 c2
class DS3,Downstream3 c3
class DS12,Downstream4 c12

```

## Mainflow logic of running Nightly pipeline: build-scripts/openjdk\*ver-pipeline

```mermaid

flowchart TD

subgraph nightly_pipeline_job

Call[build/openjdk_pipeline.groovy] --load script--> 
Load1[build/common/build_base_file.groovy]
 
Call --load config--> Load2[build/jobs/configurations/jdk*ver_pipeline_config.groovy]

Call --sharedlib --> 
Load3["git clone: openjdk-jenkins-helper"]

Load1 --call_function--> Build["function: doBuild()"]
Load2 --input--> Build
Load3 --input--> Build
end

Build --"run downstream job" -->
Done["Job:\nbuild-scripts/jobs/jdk*ver/jdk*ver-*os-*arch-*variant"]

classDef yellow fill:#ffff99
classDef c12 fill:#33ffff,stroke:#333,stroke-width:2px
class nightly_pipeline_job yellow
class Done c12

```

## Mainflow logic of running build job: build-scripts/jobs/jdk\*ver/jdk\*ver-\*os-\*arch-\*variant

```mermaid

flowchart TD

subgraph job

starter[kick_off_build.groovy] --load--> import[build/common/import_lib.groovy] --load--> Load1["build/common/openjdk_build_pipeline.groovy"] --call_function--> Builder["openjdk_build_pipeline.build()"]
Builder --sharedlib --> Load3[Git repo: openjdk-jenkins-helper]

subgraph internal_build

Load3 --> docker{"DOCKER_IMAGE"}

docker --true: run--> dockerbuild["Jenkins'call: docker.build(build-image)"] --> sign

docker --false:call_function--> CallbuildScript["buildScripts()"] --> sign{enableSigner} --true:call_function--> sign2["sign()"] --> testStage{enableTests}

sign{enableSigner} --"false" --> testStage

testStage --"true:call_function"--> smoketest["runSmokeTests()"] --> parallel{"TEST_LIST.size() > 0"}
testStage --"false"--> shouldInstaller
parallel --"false"--> shouldInstaller

subgraph parallel_tests

parallel --"true: run as stages in pipeline"-->
Stage2["runAQATests() as parallel stages"] --"invoke"-->
smokes[Job: jobs/jdk*ver/jdk*ver-<os>-<arch>-<variant>_SmokeTests]

smokes --run_job--> sanity1[sanity.openjdk]
smokes --run_job--> sanity2[sanity.system]
smokes --run_job--> sanity3[sanity.perf]
smokes --run_job--> sanity4[sanity.functional]
smokes --run_job--> extended1[extended.system]
smokes --run_job--> extended2[extended.functional]
smokes -.->|run weekly extra test job| weekly1[extended.openjdk]
smokes -.->|run weekly extra test job| weekly2[extended.perf]
smokes -.->|run weekly extra test job| weekly3[special.functional]

sanity1 --if:pass--> shouldInstaller
sanity2 --if:pass--> shouldInstaller
sanity3 --if:pass--> shouldInstaller
sanity4 --if:pass--> shouldInstaller
extended1 --if:pass--> shouldInstaller
extended2 --if:pass--> shouldInstaller
weekly1 -->|if:pass| shouldInstaller
weekly2 -->|if:pass| shouldInstaller
weekly3 -->|if:pass| shouldInstaller

end

shouldInstaller{"enableInstallers"} --true:call_function--> bI["buildInstaller()"] --call_function--> sI["signInstaller()"] --> done

shouldInstaller --"false"--> done

end

done["return(..)"]
end

classDef thisjob fill:#33ffff,stroke:#333,stroke-width:2px
classDef inner fill:#ffffff,stroke:#333,stroke-width:2px
classDef external fill:#ffe5cc,stroke:#333,stroke-width:2px
class job thisjob
class parallel_tests inner
class Load1 external

```

## Breakdown logic of build script: build/common/openjdk_build_pipeline.groovy

```mermaid

flowchart TD

subgraph build_scripts

CallbuildScript["function: buildScripts()"] --git_clone--> checkout["git clone: temurin-build"] -->
checkmac{check: Mac && !jdk8u} --true--> flagmac["Call: jmods\nSet flag: --make-exploded-image\n"] --call_script -->
scriptmake["Jenkins sh:script build-farm/make-adopt-build-farm.sh"] --> meta["Call function:\nwriteMetadata()"] --> arch["Jenkins step:\narchiveArtifacts"] --> clean2["Cleanup workspace"]
checkmac --false:call_script--> scriptmake

end

subgraph make-adopt-build-farm

bk1[Script: build-farm/make-adopt-build-farm.sh] --source--> step1[sbin/common/constants.sh] --source -->step[build-farm/set-platform-specific-configurations.sh] --call_script--> step3[makejdk-any-platform.sh]

end

subgraph makejdk-any-platform

bk1.2[Script: makejdk-any-platform.sh] --> source[various shell scripts] --> f1["Call functions:\nconfigure_build\nwriteConfigToFile"] -->
isD{USE_DOCKER} --true:run_function from docker-build.sh--> f3[buildOpenJDKViaDocker] -->dockerarg[set entrypoint to /openjdk/sbin/build.sh] --> cmd["Run: docker build"]
isD --false:run_function from native-build.sh--> f4[buildOpenJDKInNativeEnvironment] --call_script--> sbin["sbin/build.sh"]

end

subgraph build

bk1.3["Script: sbin/build.sh"] --call_function from config_init.sh--> do1.3.1["Call function:\nloadConfigFromFile\nfixJavaHomeUnderDocker\nparseArguments"] -->

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

end

subgraph writeMetadata

bk2[function: writeMetadata] --> finit{initialWrite} --false--> listArchives[Loop: installer files under target/*]
finit --true:check various files--> check1[target/metadata/*] --load--> check2["file: config/makejdk-any-platform.args"] --> listArchives
listArchives --run--> run[command: shasum on *file] --> write[output: *file.json]

end

build_scripts --> make-adopt-build-farm
build_scripts --> writeMetadata
make-adopt-build-farm --> makejdk-any-platform
makejdk-any-platform --> build

classDef color fill:#ffe5cc,stroke:#333,stroke-width:2px
classDef green fill:#66cc00,stroke:#333,stroke-width:2px
classDef blue fill:#99ccff,stroke:#333,stroke-width:2px
classDef yellow fill:#ffffcc,stroke:#333,stroke-width:2px
classDef red fill:#ff6666,stroke:#333,stroke-width:2px
class bk1,scriptmake,make-adopt-build-farm yellow
class bk1.2,step3,makejdk-any-platform blue
class bk1.3,sbin,build red
class bk2,meta,writeMetadata green
class build_scripts color

```

## High level docker image build flow

### Adoptium OpenJDK docker image(<https://hub.docker.com/_/eclipse-temurin>)

```mermaid

flowchart TD
subgraph Adoptium_OpenJDK_docker_image_main_flow

buildNew["To create docker image"] --"auto"--> use["Nightly GitHub Action"] -->auto1["Run ./update_all.sh"] --"either auto\nor manual"--> doneNew["Generate PR"]
buildNew --"manual"--> manual2["Run GitHub Action"] --> auto1
end

subgraph internal_function_call1

detailNew1["./update_all.sh"]
--> stepN1.1["source ./common_functions.sh"]
--> stepN1.2["loop $supported_versions"]
--"each"--> stepN1.3["Run ./update_multiarch.sh"]

end

subgraph internal_function_call2

detailNew2["./update_multiarch.sh"]
--> stepN2.1["source ./common_functions.sh"]
--> stepN2.2["source ./dockerfile_functions.sh"]
--> stepN2.3["loop\n$all_jvms\n$all_packages\n$oses\n$builds"]
--"each"--> check2.1{"file ${vm}_shasums_latest.sh exist"}
--"true"--> stepN2.4["source ./${vm}_shasums_latest.sh"]
-->stepN2.5["loop ${btypes}"] --"call_function"--> stepN2.6["function: generate_dockerfile"]
check2.1 --"false"--> stepN2.5
stepN2.6 --> check2.2{"if slim btype"} --"true"--> stenpN2.7["copy OS specified files"]

end

Adoptium_OpenJDK_docker_image_main_flow --> internal_function_call1
internal_function_call1 --> internal_function_call2

classDef color1 fill:#ffeeff,stroke:#333,stroke-width:2px
classDef color2 fill:#009999,stroke:#333,stroke-width:2px
class detailNew1,auto1 color1
class detailNew2,stepN1.3 color2
```

### AdoptOpenJDK docker image(<https://hub.docker.com/u/adoptopenjdk>)

```mermaid

flowchart TD

subgraph AdoptOpenJDK docker image main flow

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

end

classDef green fill:#00ff80,stroke:#333,stroke-width:2px
classDef blue fill:#0080ff,stroke:#333,stroke-width:2px
class stage1 green
class stage2 blue
```

```mermaid
flowchart TD

subgraph detail_internal_function_call1

Stage1[Docker build stage] --call_function --> build[dockerBuild]
--> login1[login dockerhub] --git pull--> git1[openjdk-docker.git] --call--> ba[Script: build_all.sh]

end

subgraph detail_internal_function_call2

Stage2[Docker Manifest] --call_function --> mani[dockerManifest]
--> login2[login dockerhub] --git pull--> git2[openjdk-docker.git] --call--> ua[Script: update_manifest_all.sh]

end

subgraph internal_function_calls

subgraph build1

buildall[Script: build_all.sh] -->s1[handle .summary_table file] --> s2[load common_functions.sh] --> s3[clenaup .summary_table file] --> s4[cleanup env: container, image, menifests , temp scripts] --call_script--> s5[Script: build_latest] -->result{build failed or push_commands.sh non-exist} --false-->s6[Run: push_commands.sh]

end

s6--test image-->s7[Run: test_multiarch.sh]

subgraph build2

updateall["Script: update_manifest_all.sh"] --source--> c1[common_functions.sh] --"loop:supported_versions"-->

c2["Call functions:\ncleanup_images\ncleanup_manifest"] -->

c3["Call scripts:\n generate_manifest_script.sh\nmanifest_commands.sh"]

end

end

c3--"test image"-->s7

detail_internal_function_call1 --> internal_function_calls
detail_internal_function_call2 --> internal_function_calls


classDef green fill:#00ff80,stroke:#333,stroke-width:2px
classDef blue fill:#0080ff,stroke:#333,stroke-width:2px
classDef yellow fill:#cccc00,stroke:#333,stroke-width:2px
classDef red fill:#ff99ff,stroke:#333,stroke-width:2px
class Stage1 green
class Stage2 blue
class buildall,ba yellow
class updateall,ua red

```
