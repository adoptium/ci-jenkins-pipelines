import groovy.json.JsonSlurper

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

// Don't parameterise url as we currently have no need and the job generates its own params anyway
String branch = "${ghprbActualCommit}"
String DEFAULTS_FILE_URL = "https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/${branch}/pipelines/defaults.json"

String url = DEFAULTS_JSON['repository']['pipeline_url']
Closure prTest

// Switch to controller node to load library groovy definitions
node("worker") {
    checkout([
        $class: 'GitSCM',
        branches: [[name: branch]],
        userRemoteConfigs: [[
            refspec: " +refs/pull/*/head:refs/remotes/origin/pr/*/head +refs/heads/master:refs/remotes/origin/master +refs/heads/*:refs/remotes/origin/*",
            url: url
        ]]
    ])

    library(identifier: 'openjdk-jenkins-helper@master')
    prTest = load DEFAULTS_JSON['scriptDirectories']['tester']
}

// Run tests outside node context
prTest(
        branch,
        currentBuild,
        this,
        url,
        DEFAULTS_JSON
).runTests()

