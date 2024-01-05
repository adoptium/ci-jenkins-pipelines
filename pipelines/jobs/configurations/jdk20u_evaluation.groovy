targetConfigurations = [
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// if set to empty string then it wont get triggered
triggerSchedule_evaluation = 'TZ=UTC\n30 03 * * 2,4,6'
// if set to empty string then it wont get triggered
triggerSchedule_weekly_evaluation= 'TZ=UTC\n05 17 * * 7'

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this