targetConfigurations = []
        // 'aarch64Linux': [
        //         'hotspot'
        // ],
        // 'aarch64Windows' : [
        //         'temurin'
        // ],
        // 'riscv64Linux': [
        //         'temurin'
        // ]

// empty string as it wont get triggered now
triggerSchedule_evaluation = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_evaluation= ''

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this