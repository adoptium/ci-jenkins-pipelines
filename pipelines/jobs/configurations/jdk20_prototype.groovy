targetConfigurations = [
        'aarch64Linux': [
                'hotspot'
        ],
        'aarch64Windows' : [
                'temurin'
        ],
        'riscv64Linux': [
                'temurin'
        ]
]

// 03:30 Wed, Fri
triggerSchedule_prototype = 'TZ=UTC\nH 03 * * 3'
// 23:30 Sat
triggerSchedule_weekly_prototype = 'TZ=UTC\n30 23 * * 6'

// scmReferences to use for weekly prototype release build
weekly_prototype_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this