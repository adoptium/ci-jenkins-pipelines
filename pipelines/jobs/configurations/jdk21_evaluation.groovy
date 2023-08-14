targetConfigurations = [
        'riscv64Linux': [
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// if set to empty string then it wont get triggered

// 23:40 Mon, Wed
//Uses releaseTrigger_21ea: triggerSchedule_evaluation = 'TZ=UTC\n40 23 * * 1,3'
// 23:40 Sat
//Replaced by releaseTrigger_21ea:triggerSchedule_weekly_evaluation = 'TZ=UTC\n40 23 * * 6'

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this
