@Library('NodeHelper') _
import jenkins.model.Jenkins;
import hudson.model.Computer;

clones = [:]
result = [:]

/* Iterates over all the online nodes in Jenkins and
 * prints contents of workspace folder along with
 * the space they occupy. With exception of tmp directory
 * tmp directory is any directory with "@tmp" in the name
 * 
 * The nodes can be either passed in or grabbed from Jenkins
 */
stage('Pepare_Space_Monitoring_Jobs') {
    NodeHelper nodeHelper = new NodeHelper();

    String projectLabel
    if (params.projectLabel.length() > 1) {
        projectLabel = params.projectLabel
    } else {
        projectLabel = "all"
    }

    ArrayList<Computer> computers = new ArrayList<Computer>()
    if (params.machines.length() > 1) {
        String[] inputMachines = params.machines.split(",")
        for (String inputMachine : inputMachines) {
            computers.add(nodeHelper.getComputer(inputMachine))
        }
    } else {
        def currentInstance = Jenkins.getInstance()
        if (currentInstance != null) {
            computers = currentInstance.getComputers();
        }
    }


    for (Computer computer : computers) {
        String machineName = computer.getName();

        if (computer.isOnline() && computer.getName() != "") {
            String kernelName = nodeHelper.getOsKernelInfo(computer.getName()).get(0).toLowerCase()

            if (projectLabel.equals("all")
                        || nodeHelper.getLabels(computer.getName()).contains(projectLabel)) {

                String workspaceDirectory = nodeHelper.getHomeDirectoryPath(machineName)

                String workspaceStatscmd
                String subdirectoryStatscmd
                String statOutputHeading

                switch (kernelName) {
                    case 'linux':
                        workspaceDirectory += "/workspace"
                        workspaceStatscmd = '#!/bin/sh -e\n' + 'du -sh ' + workspaceDirectory
                        subdirectoryStatscmd = '#!/bin/sh -e\n du -sh ' + workspaceDirectory + '/* | sort -hr'
                        statOutputHeading = machineName
                        break;
                    case 'aix':
                        workspaceDirectory += "/workspace"
                        workspaceStatscmd = '#!/bin/sh -e\n' + 'du -sg ' + workspaceDirectory
                        subdirectoryStatscmd = '#!/bin/sh -e\n du -sg ' + workspaceDirectory + '/* | sort -nr'
                        statOutputHeading = machineName + ' in GB'
                        break;
                    case 'mac':
                        workspaceDirectory += "/workspace"
                        workspaceStatscmd = '#!/bin/sh -e\n' + 'du -sh ' + workspaceDirectory
                        subdirectoryStatscmd = '#!/bin/sh -e\n du -sh ' + workspaceDirectory + '/* | sort -nr'
                        statOutputHeading = machineName
                        break;
                    /* This is commented out because it takes way too long to return
                     * it isn't a top priority
                     * dir /s /-c
                     */
                    case 'windows':
                        // workspaceDirectory += "\\workspace"
                        // workspaceStatscmd = '#!/bin/sh -e\n du -sh ' + workspaceDirectory
                        // subdirectoryStatscmd = '#!/bin/sh -e\n du -sh '  workspaceDirectory  '\\* | sort -rn'
                        // statOutputHeading = machineName
                        // break;
                    case 'zos':
                    default:
                        result[machineName] = "Support for ${kernelName} is yet to be implemented"
                        break;
                }

                if (workspaceStatscmd != null && subdirectoryStatscmd != null) {
                    setupParallelPipelines(
                            timeOut,
                            machineName,
                            workspaceDirectory,
                            workspaceStatscmd,
                            subdirectoryStatscmd,
                            statOutputHeading)
                } else {
                    result[machineName] = "Support for ${kernelName} is yet to be implemented"
                }
            }
        }
    }
    currentInstance = null;
    computers = null;
}


@NonCPS
def beautify(machineName, workspaceDirectory, workspaceStats, subdirectories) {
    workspaceStats = workspaceStats.replaceAll("\\s+", "        ");
    subdirectories = subdirectories.replace(workspaceDirectory + "/", "");
    String output = "\n\n=======================================================================\n";
    output += "Disk stats for ${machineName}"
    output += "\nWorkspace:\n${workspaceStats}";
    def subdirectoriesArray = subdirectories.split("\n");
    for (String line : subdirectoriesArray) {
        if (!line.contains("tmp")) {
            line = line.replaceAll("\\s+", "    ")
            output += "\n    ${line}";
        }
    }
    output += "\n=======================================================================\n\n";
    return output
}

def setupParallelPipelines(
        timeOut,
        machineName,
        workspaceDirectory,
        workspaceStatscmd,
        subdirectoryStatscmd,
        statOutputHeading
        ) {

    clones[machineName] = {
        try {
            timeout(time: params.timeout as Integer, unit: 'HOURS') {
                node (machineName) 
                {
                    String workspaceStats = sh (
                            script: workspaceStatscmd,
                            returnStdout: true).trim()

                    String subdirectories = sh (
                            script: subdirectoryStatscmd,
                            returnStdout: true).trim()

                    println beautify(statOutputHeading, workspaceDirectory, workspaceStats,subdirectories)
                }
            }
        } catch(err) {
            if (err.message != null) {
                result[machineName] = err.message
            }
        }
    }
}

parallel clones

if (!result.isEmpty()) { // Checks if any errors were caught
    println "About to print error messages"
    String errors = "\n" // This is so that the first line after "Error" in the console
    for (Map<String, String> entry : result) {
        errors += entry.key + ":" + entry.value + "\n\n"
    }

    error (errors)
}

