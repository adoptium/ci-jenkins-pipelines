package common

import java.nio.file.NoSuchFileException
import java.net.MalformedURLException
import groovy.json.JsonSlurper

class RepoHandler {
    private final def context
    private final Map configs
    private final Map ADOPT_DEFAULTS_JSON
    private Map USER_DEFAULTS_JSON

    private final String ADOPT_JENKINS_DEFAULTS_URL = "https://raw.githubusercontent.com/AdoptOpenJDK/ci-jenkins-pipelines/master/pipelines/defaults.json"

    /*
    Constructor
    */
    RepoHandler (def context, Map<String, ?> configs) {
        this.context = context
        this.configs = configs

        def getAdopt = new URL(ADOPT_JENKINS_DEFAULTS_URL).openConnection()
        this.ADOPT_DEFAULTS_JSON = new JsonSlurper().parseText(getAdopt.getInputStream().getText()) as Map
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
    Setter to retrieve and/or save a user ci-jenkins-pipelines defaults json inside the object. It can read the JSON from a remote or local source.
    */
    public Map<String, ?> setUserDefaultsJson(def context, def content) {
        try {
            def getUser = new URL(content).openConnection()
            this.USER_DEFAULTS_JSON = new JsonSlurper().parseText(getUser.getInputStream().getText()) as Map
        } catch (MalformedURLException e) {
            context.println "[WARNING] Given path for setUserDefaultsJson() is malformed or not valid. Attempting to parse the path as a JSON string..."
            this.USER_DEFAULTS_JSON = new JsonSlurper().parseText(content) as Map
        }
    }

    /*
    Changes dir to adopt's ci-jenkins-pipelines repo
    */
    public void checkoutAdoptPipelines () {
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: ADOPT_DEFAULTS_JSON["repositories"]["pipeline_branch"] ] ],
            userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repositories"]["pipeline_url"] ] ]
        ])
    }

    /*
    Changes dir to the user's ci-jenkins-pipelines repo
    */
    public void checkoutUserPipelines () {
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: configs["branch"] ] ],
            userRemoteConfigs: [ configs["remotes"] ]
        ])
    }

    /*
    Changes dir to adopt's openjdk-build repo
    */
    public void checkoutAdoptBuild () {
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: ADOPT_DEFAULTS_JSON["repository"]["build_branch"] ] ],
            userRemoteConfigs: [ [ url: ADOPT_DEFAULTS_JSON["repository"]["build_url"] ] ]
        ])
    }

    /*
    Changes dir to user's openjdk-build repo
    */
    public void checkoutUserBuild () {
        context.checkout([$class: 'GitSCM',
            branches: [ [ name: USER_DEFAULTS_JSON["repository"]["build_branch"] ] ],
            userRemoteConfigs: [ [ url: USER_DEFAULTS_JSON["repository"]["build_url"] ] ]
        ])
    }

}