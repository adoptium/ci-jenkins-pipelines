@Library('NodeHelper') _

node {
    String[] machineNames = params.machineNames.trim().split(",")
    def nodeHelper = new NodeHelper();

    /* if the flag updateDescription is not set we only update the labels
     * The user can chose to either overwrite, add preconfigured labels or append labels
     *
     * To overwrite labels, the overwrite flag must be set
     * To add preconfigured labels, only the manchine names need to passed in
     * To append labels, a set of label(s) must be passed in
     */
    stage('Update_Labels') {
        String[] labels;
        if (!params.labels.isEmpty()) {
            labels = params.labels.trim().split(",")
        }

        for (int index = 0; index < machineNames.length; index++) {
            println "Machine ${machineNames[index]} old labels: ${nodeHelper.getLabels(machineNames[index])}"

            if (!params.overWriteLabels && params.projectLabel.isEmpty()) {
                // We shoulidn't be touching any machine if a project label isn't is passed
                error("Neither project label was provied nor overWriteLabels flag was set")
            }
            
            
            if (params.overWriteLabels) {

                if (labels == null) { // No labels have been supplied so we'll overwrite just with preconfigured labels 
                    String constructedLabels = "${nodeHelper.constructLabels(machineNames[index])}"
                    println "Machine ${machineNames[index]} updated labels: ${nodeHelper.addLabel(machineNames[index],constructedLabels)}"
                } else { // else overwrite with the supplied labels
                    println "Machine ${machineNames[index]} updated labels: ${nodeHelper.addLabel(machineNames[index], labels[index%labels.length])}"    
                }

            } else if (params.projectLabel.equals("all")
                        || nodeHelper.getLabels(machineNames[index]).contains(params.projectLabel)) {

                if (labels == null) { // Add preconfigured labels

                    String constructedLabels = "${params.projectLabel} ${nodeHelper.constructLabels(machineNames[index])}"
                    println "Machine ${machineNames[index]} updated labels: ${nodeHelper.addLabel(machineNames[index],constructedLabels)}"

                } else { // Append labels
                    println "Machine ${machineNames[index]} updated labels: ${nodeHelper.appendLabel(machineNames[index], labels[index%labels.length])}"
                }

            }
        }
    }
    if (params.updateDescription) {
        /* Update the description if the updateDescription flag is set
         * This stage assumes that the description doesn't already have RAM, CPU and disk info
         */
        stage('Update_Description') {
            // def nodeHelper = new NodeHelper();

            for (int index = 0; index < machineNames.length; index++) {
                println "Pre-update description of ${machineNames[index]}: ${nodeHelper.getDescription(machineNames[index])}"
                if (params.projectLabel.equals("all")
                        || nodeHelper.getLabels(machineNames[index]).contains(params.projectLabel)) {
                    
                    String description = nodeHelper.getDescription(machineNames[index]);
                    description += " - ${nodeHelper.getCpuCount(machineNames[index])}CPU";
                    description += " ${nodeHelper.getMemory(machineNames[index]).get(0)} RAM";
                    description += " ${nodeHelper.getTotalSpace(machineNames[index])} Disk";
                    
                    String updatedDescription = nodeHelper.setDescription(machineNames[index], description);
                    println "Description of ${machineNames[index]} has been updated to ${updatedDescription}";

                } else {
                    println "Machine specified ${machineNames[index]} does not belong to the project ${params.projectLabel}";
                }
            }
        }
    }
}
