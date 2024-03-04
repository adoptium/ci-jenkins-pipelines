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
package common

import java.nio.file.NoSuchFileException
import groovy.json.JsonSlurper

class RepoHandler {
    private final Map configs
    private final Map ADOPT_DEFAULTS_JSON
    private final String pipeline_branch_override
    private final String build_branch_override
    private Map USER_DEFAULTS_JSON

    private final String ADOPT_JENKINS_DEFAULTS_MASTER_URL = "https://raw.githubusercontent.com/adoptium/ci-jenkins-pipelines/master/pipelines/defaults.json"

    /*
    Constructor: Adopt defaults from ADOPT_JENKINS_DEFAULTS_MASTER_URL
    */
    RepoHandler (Map<String, ?> configs) {
        this.configs = configs

        // Use "master" branch Adopt defaults
        def getAdopt = new URL(ADOPT_JENKINS_DEFAULTS_MASTER_URL).openConnection()
        this.ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
       
        // No branch overrides
        this.pipeline_branch_override = null
        this.build_branch_override = null
    }

    /*
    Constructor: Adopt defaults from caller, and optional pipeline/build branch overrides
    */
    RepoHandler (Map<String, ?> configs, Map adoptDefaults, String pipeline_branch_override, String build_branch_override) {
        this.configs = configs

        // Callers Adopt defaults and optional branch overrides
        this.ADOPT_DEFAULTS_JSON = adoptDefaults
        this.pipeline_branch_override = pipeline_branch_override
        this.build_branch_override = build_branch_override
    }

    /*
    Getter to retrieve user's git ci-jenkins-pipeline remote config
    */
    public Map<String, ?> getUserRemoteConfigs() {
        return configs
    }

    /*
    Getter to retrieve adopt's ci-jenkins-pipelines defaults
    */
    public Map<String, ?> getAdoptDefaultsJson() {
        return ADOPT_DEFAULTS_JSON
    }

    /*
    Getter to retrieve user's ci-jenkins-pipelines defaults
    */
    public Map<String, ?> getUserDefaultsJson() {
        return USER_DEFAULTS_JSON
    }

    /*
    Setter to retrieve and/or save a user ci-jenkins-pipelines defaults json inside the object. It can read the JSON from a remote or local source or just assign a map directly if it already is one.
    */
    public Map<String, ?> setUserDefaultsJson(def context, def content) {
        if (content instanceof Map) {
            this.USER_DEFAULTS_JSON = content
        } else {
            try {
                def getUser = new URL(content).openConnection()
                this.USER_DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
            } catch (Exception e) {
                context.println "[WARNING] Given path for setUserDefaultsJson() is malformed or not valid. Attempting to parse the path as a JSON string..."
                this.USER_DEFAULTS_JSON = new JsonSlurper().parseText(content) as Map
            }
        }
    }

    /*
    Changes dir to adopt's ci-jenkins-pipelines repo
    */
    public void checkoutAdoptPipelines (def context) {
        def branch = this.pipeline_branch_override ?: "${ADOPT_DEFAULTS_JSON['repository']['pipeline_branch']}"
   
        context.println "[CHECKOUT] Checking out Adopt Pipelines ${ADOPT_DEFAULTS_JSON['repository']['pipeline_url']} : ${branch}"
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: "${branch}" ] ],
            userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repository"]["pipeline_url"] ] ]
        ])
    }

    /*
    Changes dir to the user's ci-jenkins-pipelines repo
    */
    public void checkoutUserPipelines (def context) {
        def branch = this.pipeline_branch_override ?: "${configs['branch']}"

        context.println "[CHECKOUT] Checking out User Pipelines ${configs['remotes']['url']} : ${branch}"
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: "${branch}" ] ],
            userRemoteConfigs: [ configs["remotes"] ]
        ])
    }

    /*
    Changes dir to adopt's temurin-build repo
    */
    public void checkoutAdoptBuild (def context) {
        def branch = this.build_branch_override ?: "${ADOPT_DEFAULTS_JSON['repository']['build_branch']}"

        context.println "[CHECKOUT] Checking out Adopt Build ${ADOPT_DEFAULTS_JSON['repository']['build_url']} : ${branch}"
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: "${branch}" ] ],
            userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repository"]["build_url"] ] ]
        ])
    }

    /*
    Changes dir to user's temurin-build repo
    */
    public void checkoutUserBuild (def context) {
        def branch = this.build_branch_override ?: "${USER_DEFAULTS_JSON['repository']['build_branch']}"

        context.println "[CHECKOUT] Checking out User Build ${USER_DEFAULTS_JSON['repository']['build_url']} : ${branch}"
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: "${branch}" ] ],
            userRemoteConfigs: [ [ url: USER_DEFAULTS_JSON["repository"]["build_url"] ] ]
        ])
    }

}
