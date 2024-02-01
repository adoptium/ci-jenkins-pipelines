package common
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

/*
  Detect new upstream OpenJDK source build tag, and trigger a "beta" pipeline build
  if the given build has not already been published.
  The "Force" option can be used to re-build and re-publish and existing build.
*/

node('worker') {

    def mirrorRepo="https://github.com/${params.MIRROR_ORG}/${params.JDK_VERSION}"
    def version="${params.JDK_VERSION}".replaceAll("u", "").replaceAll("jdk", "").toInteger()
    def binariesRepo="https://github.com/${params.BINARIES_REPO}".replaceAll("_NN_", version)

    def triggerBuild = false

    latestTag=sh(script: "git ls-remote --sort=-v:refname --tags "${mirrorRepo}" | grep -v "\^{}" | grep -v "\+0\$" | grep -v "\-ga\$" | grep "_adopt" | tr -s "\t " " " | cut -d" " -f2 | sed "s,refs/tags/,," | sort -V -r | head -1", returnStdout:true)
    echo "latest tag = ${latestTag}"
    if (!latestTag.contains("_adopt") {
       echo Latest tag does not have _adopt - aborting
       throw new Exception("Failed to find the latest _adopt build tag")
    }

    def buildTag=latestTag.replaceAll("_adopt","-ea-beta")
    def publishTag=latestTag.replaceAll("_adopt","-ea")

    if (!params.FORCE) {
        // Check binaries repo for existance of the given release?
        desiredRepoTagURL="${binariesRepo}/releases/tag/${buildTag}"
        httpCode=sh(script:"curl -s -o /dev/null -w "%{http_code}" "$desiredRepoTagURL"", returnStatus:true)
        if (httpCode == 200) {
            echo Release "$buildTag" already published - nothing to do
        } else if (httpCode == 404) {
            echo New unpublished build tag "${buildTag}" - triggering build
            triggerBuild = true
        } else {
            error =  "Unexpected HTTP code "${HTTPCODE}" when looking got $desiredRepoTagURL"
            echo "${error}"
            throw new Exception("${error}")
        }
    } else {
        echo "FORCE is true, triggering build.."
        triggerBuild = true
    }

    if (triggerBuild) {
        // Set version suffix, jdk8 has different mechanism to jdk11+
        def additionalConfigureArgs =  (version > 8) ? "--with-version-opt=ea" : "--with-user-release-suffix=ea"

        // Trigger pipline build of the new build tag and publish with the "ea" tag
        def buildPipeline = "build-scripts/openjdk${version}-pipeline"
        stage("Trigger build pipeline - ${buildPipeline}") {
            def job = build job: "${params.buildPipeline}",
                            parameters: [
                                string(name: 'releaseType',             value: "Weekly Without Publish"),
                                string(name: 'scmReference',            value: "$latestTag"),
                                string(name: 'overridePublishName',     value: "$publishTag"),
                                string(name: 'additionalConfigureArgs', value: "$additionalConfigureArgs"),
                            ]
            echo "Triggered pipeline build result = "+ job.getResult()
            currentBuild.result = job.getResult()
        }
    }
}

