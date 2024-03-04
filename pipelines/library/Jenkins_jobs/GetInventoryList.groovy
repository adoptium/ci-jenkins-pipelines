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
Parameters: 
SETUP_LABEL: String - the label of the node that this job runs on. The default is worker
LABEL: String - the label of the machines that this job will look for
CREATE_INVENTORY_INI: Boolean - This toggles if the job will create the inventory ini file
CREATE_INVENTORY_SUMMARY: Boolean - This toggles if the summary table will be generated
SLACK_CHANNEL: String - What channel a list of problematic machines gets sent to. If this is empty, no slack message will be sent
SSH_CREDENTIALS: Credentials - The credentials needed to push the files to git
GIT_REPO: String - The git repo to push the files to. If this is not set, the files will be archived to Jenkins
GIT_BRANCH: String - The git branch to push the files to. It defaults to master
GIT_FOLDER: String - The folder that the files will be saved to. It defaults to a null string
*/

@Library('NodeHelper') _

def setupLabel = (params.SETUP_LABEL ? params.SETUP_LABEL : 'worker')
node(setupLabel){
    stage('set up'){
        checkout scm
        jenkinsNodes = (params.LABEL ? jenkins.model.Jenkins.instance.getLabel(params.LABEL).getNodes() : jenkins.model.Jenkins.instance.getNodes())
        nodeList = []
    }
    
    stage('Get Node information'){
        def parallelJob = [:]

        jenkinsNodes.each() { node ->
            parallelJob["${node.getNodeName()}"] = {
                nodeList.add(getNodeConfig(node))
            }
        }
        parallel parallelJob 
    }
    gitFolder = (params.GIT_FOLDER ? params.GIT_FOLDER + '/' : '')
    outputFiles = []
    if (params.CREATE_INVENTORY_SUMMARY){
        stage('Create Inventory Summary'){
            createTable(nodeList)
        }
    }
    if (params.CREATE_INVENTORY_INI){
        stage('Create Inventory INI'){
            createInventoryList(nodeList)
        }
    }
    if (params.CREATE_INVENTORY_XML){
        stage('Create Inventory XML'){
            echo 'XML creation is not supported yet'
        }
    }
    if (params.SLACK_CHANNEL){
        sendSlack(nodeList)
    }

    stage('Archive'){
        cleanWs()
        if (outputFiles){
            if(params.GIT_REPO){
                def date = new Date()
                stash name: 'files for git', includes: outputFiles.join(',') 
                dir('git_repo'){
                    def gitBranch = (params.GIT_BRANCH ? params.GIT_BRANCH : 'master')
                    git url: params.GIT_REPO, branch: gitBranch, credentials: params.SSH_CREDENTIALS
                    unstash name: 'files for git'
                    sshagent([params.SSH_CREDENTIALS]) {            
                        sh """\
                        git add ${outputFiles.join(' ')}
                        git commit -m "This File has been updated on ${date} from Jenkins"
                        git push origin ${gitBranch}
                        """
                    }
                }
            } else {
                archiveArtifacts outputFiles.join(', ')
            }
        }

    }
}

def getNodeConfig(node){
    
    def nodeHelper = new NodeHelper()
    def machineConfig = [:]
    def nodeName = node.getNodeName()
    def slaveError = false
    def labels =  nodeHelper.getLabels(nodeName)
    
    machineConfig.put('NODE_NAME', nodeName)
    machineConfig.put('HOST_NAME', nodeHelper.getHostName(nodeName))

    def arch = (labels =~ ~/hw\.arch\.(\w+)/)[0][1]
    def os_version = ( labels =~ /sw\.os\.(\w+)\.(\w+)/)
    def os = os_version[0][1]
    def osVersion = os_version[0][2]
    osVersion = osVersion.replace('_', '.')

    def buildType = 'none'
    if (labels.contains('test') && labels.contains('build')){
        buildType = 'Build and Test'
    }else if (labels.contains('build')){
        buildType = 'Build'
    } else if (labels.contains('test')){
        buildType = 'Test'
    }

    try {
        arch = nodeHelper.getOsArch(nodeName)
    }
    catch (any){
        echo 'Unable to get the ARCH, Getting it from the labels'
        slaveError = true
    }
    
    arch = (arch == 'amd64' || arch == 'x86_64') ? 'x86' : arch

    offlineCause = nodeHelper.getOfflineReason(nodeName)

    machineConfig.put('ARCH', arch) 
    machineConfig.put('OS', os)
    machineConfig.put('OS_VERSION', osVersion)
    machineConfig.put('IS_ONLINE', nodeHelper.nodeIsOnline(nodeName))
    machineConfig.put('REASON', offlineCause.split('\n')[0])
    machineConfig.put('CONNECTION_ERROR', slaveError)
    machineConfig.put('BUILD_TYPE', buildType)

    return machineConfig
}

def createTable(nodeList){
    list = getTabelElements(nodeList)
    def lines = [] 
    lines.add('| OS | VERSION | ARCH | BUILD TYPE | NUMBER |')
    lines.add('| --- | --- | --- | --- | --- |')
    list.each() { bla, config ->
        lines.add('|' + [config.get('OS'), config.get('OS_VERSION'), config.get('ARCH'), config.get('BUILD_TYPE'), config.get('NUMBER')].join('|') + '|')
    }
    def output = "${gitFolder}summary.md"
    outputFiles.add(output)
    writeFile file: output, text: lines.join('\n')
}

def sendSlack(nodeList){
    notifyList = []
    errorList = []
    nodeList.each{ config ->
        if(config.get('IS_ONLINE') == false){
            notifyList.add("${config.get('NODE_NAME')}: ${config.get('REASON')}")
        } else if (config.get('CONNECTION_ERROR')){
            notifyList.add("${config.get('NODE_NAME')}: Node Helper had a problem reaching the machine")
        } else if (config.get('BUILD_TYPE') == 'none'){
            notifyList.add("${config.get('NODE_NAME')}: There is no Build or Test label for this machine")
        }
    }
    if (notifyList && params.SLACK_CHANNEL){
        slackSend channel: params.SLACK_CHANNEL, color: 'danger', message: 'These Machines are problematic:\n' + notifyList.join('\n')
    }
}

def machineTree(nodeList){
    def tree = [:]

    nodeList.each() { node ->
        def os = node.get('OS')
        def osVersion = node.get('OS_VERSION')
        def arch = node.get('ARCH')
        
        if (tree[arch] == null){
            tree[arch] = [:]
        }
        if (tree[arch][os] == null){
            tree[arch][os] = [:]
        }
        if (tree[arch][os][osVersion] == null){
            tree[arch][os][osVersion] = []
        }
        tree[arch][os][osVersion].add(node)
    }
    return ['tree': tree]
}

def createInventoryList(nodeList){
    def machines = machineTree(nodeList)
    def templateFile = readFile 'Jenkins_jobs/inventory-ini.template'
    def engine = new groovy.text.SimpleTemplateEngine()
    def template = engine.createTemplate(templateFile).make(machines)

    def output = "${gitFolder}inventory.ini"
    outputFiles.add(output)
    writeFile file: output, text: template.toString()
}

def getTabelElements(nodeList){
    def tableInfo = [:]

    nodeList.each() { node ->
        def os = node.get('OS')
        def osVersion = node.get('OS_VERSION')
        def arch = node.get('ARCH')
        def buildType = node.get('BUILD_TYPE')
        def identifier = os + osVersion + arch + buildType

        if (tableInfo.get(identifier) == null){
            tableInfo[identifier] = [
                OS          : os,
                OS_VERSION  : osVersion,
                ARCH        : arch,
                BUILD_TYPE  : buildType,
                NUMBER      : 1
            ]
        } else {
            tableInfo[identifier]['NUMBER'] += 1
        }
    }
    return tableInfo
}