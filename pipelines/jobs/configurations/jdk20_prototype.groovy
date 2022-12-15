targetConfigurations = [
        // 'aarch64Linux': [
        //         'hotspot'
        // ],
        // 'aarch64Windows' : [
        //         'temurin'
        // ],
        // 'riscv64Linux': [
        //         'temurin'
        // ]
]

// empty string as it wont get triggered now
triggerSchedule_prototype = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_prototype = ''

// scmReferences to use for weekly prototype release build
weekly_prototype_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this