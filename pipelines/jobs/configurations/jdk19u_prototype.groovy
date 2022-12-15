targetConfigurations = [
        // 'aarch64Windows': [ 'temurin' ]
]

// empty string as it wont get triggered now
triggerSchedule_prototype = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_prototype = ''

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : ''
]
return this
