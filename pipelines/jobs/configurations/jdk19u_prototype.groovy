targetConfigurations = [
        'aarch64Windows': [ 'temurin' ]
]

// 03:30 Tue, Thur, Sat
triggerSchedule_prototype = 'TZ=UTC\n30 03 * * 2,4,6'
// 17:05 Sun
triggerSchedule_weekly_prototype = 'TZ=UTC\n05 17 * * 7'

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : ''
]
return this
