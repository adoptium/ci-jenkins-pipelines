targetConfigurations = [
        'aarch64Linux': [
                'hotspot'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// 03:30 Wed, Fri
triggerSchedule_prototype = 'TZ=UTC\nH 23 * * 6'

return this