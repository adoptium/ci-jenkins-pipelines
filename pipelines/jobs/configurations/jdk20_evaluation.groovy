targetConfigurations = [
        'riscv64Linux': [
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ]
]
        // 'aarch64Linux': [
        //         'hotspot'
        // ],
        // 'aarch64Windows' : [
        //         'temurin'
        // ],


// empty string as it wont get triggered
triggerSchedule_evaluation = 'TZ=UTC\n30 03 * * 2,4,6'
// empty string as it wont get triggered
triggerSchedule_weekly_evaluation= 'TZ=UTC\n05 17 * * 7'

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this