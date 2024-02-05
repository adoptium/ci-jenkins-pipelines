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
  if the given build has not already been published, and the given version has
  not GA'd (existance of -ga tag).

  The "Force" option can be used to re-build and re-publish the existing latest build.
*/

node('worker') {

    def mirrorRepo="https://github.com/${params.MIRROR_ORG}/${params.JDK_VERSION}"
    def version="${params.JDK_VERSION}".replaceAll("u", "").replaceAll("jdk", "").toInteger()
    def binariesRepo="https://github.com/${params.BINARIES_REPO}".replaceAll("_NN_", "${version}")

    def triggerBuild = false

    // Find latest _adopt tag for this version?
    def latestAdoptTag=sh(script:'git ls-remote --sort=-v:refname --tags "'+mirrorRepo+'" | grep -v "\\^{}" | grep -v "\\+0\\$" | grep -v "\\-ga\\$" | grep "_adopt" | tr -s "\\t " " " | cut -d" " -f2 | sed "s,refs/tags/,," | sort -V -r | head -1 | tr -d "\\n"', returnStdout:true)
    if (latestAdoptTag.indexOf("_adopt") < 0) {
        def error="Error finding latest _adopt tag for ${mirrorRepo}"
        echo "${error}"
        throw new Exception("${error}")
    }
    echo "Latest Adoptium tag from ${mirrorRepo} = ${latestAdoptTag}"

    def buildTag=latestAdoptTag.replaceAll("_adopt","-ea-beta")
    def publishTag=latestAdoptTag.replaceAll("_adopt","-ea")

    if (!params.FORCE) {
        // Determine this versions potential GA tag, so as to not build and publish a GA'd version
        def gaTag
        def versionStr
        if (version > 8) {
            versionStr=latestAdoptTag.substring(0, latestAdoptTag.indexOf("+"))
        } else {
            versionStr=latestAdoptTag.substring(0, latestAdoptTag.indexOf("-"))
        }
        gaTag=versionStr+"-ga"
        echo "Expected GA tag to check for = ${gaTag}"
    
        def gaTagCheck=sh(script:'git ls-remote --sort=-v:refname --tags "'+mirrorRepo+'" | grep -v "\\^{}" | grep "'+gaTag+'"', returnStatus:true)
        if (gaTagCheck == 0) {
            echo "Version "+versionStr+" has already GA'd - nothing to do"
        } else {
            echo "This version has not GA'd yet, checking if ${buildTag} is already published?"

            // Check binaries repo for existance of the given release?
            def desiredRepoTagURL="${binariesRepo}/releases/tag/${buildTag}"
            def httpCode=sh(script:"curl -s -o /dev/null -w '%{http_code}' "+desiredRepoTagURL, returnStdout:true)
            if (httpCode == "200") {
                echo "Build tag $buildTag is already published - nothing to do"
            } else if (httpCode == "404") {
                echo "New unpublished build tag ${buildTag} - triggering build"
                triggerBuild = true
            } else {
                def error =  "Unexpected HTTP code ${httpCode} when querying for existing build tag at $desiredRepoTagURL"
                echo "${error}"
                throw new Exception("${error}")
            }
        }
    } else {
        echo "FORCE is true, triggering build.."
        triggerBuild = true
    }

    if (triggerBuild) {
        // Set version suffix, jdk8 has different mechanism to jdk11+
        def additionalConfigureArgs =  (version > 8) ? "--with-version-opt=ea" : "--with-milestone=beta"

        // Trigger pipeline builds for main & evaluation of the new build tag and publish with the "ea" tag
        def jobs = [:]
        def pipelines = ["build-scripts/openjdk${version}-pipeline", "build-scripts/evaluation-openjdk${version}-pipeline"]

        pipelines.each { pipeline ->
            jobs[pipeline] = {
                catchError {
                    stage("Trigger build pipeline - ${pipeline}") {
                        echo "Triggering ${pipeline} for $latestAdoptTag"

                        def job = build job: "${pipeline}", propagate: true,
                            parameters: [
                                string(name: 'releaseType',             value: "Weekly"),
                                string(name: 'scmReference',            value: "$latestAdoptTag"),
                                string(name: 'overridePublishName',     value: "$publishTag"),
                                string(name: 'additionalConfigureArgs', value: "$additionalConfigureArgs")
                            ]
                        echo "Triggered ${pipeline} build result = "+ job.getResult()
                    }
                }
            }
        }

        parallel jobs
    }
}

