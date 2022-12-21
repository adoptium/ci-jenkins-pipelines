targetConfigurations = [
        // 'aarch64Windows': [ 'temurin' ]
]

// empty string as it wont get triggered now
triggerSchedule_evaluation = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_evaluation= ''

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : ''
]
return this
