targetConfigurations = [
        'x64Mac'      : [
                'temurin'
        ],
        'x64Linux'    : [
                'temurin'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'x64Windows'  : [
                'temurin'
        ],
        'x32Windows'  : [
                'temurin'
        ],
        'ppc64Aix'    : [
                'temurin'
        ],
        'ppc64leLinux': [
                'temurin'
        ],
        's390xLinux'  : [
                'temurin'
        ],
        'aarch64Linux': [
                'hotspot',
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'aarch64Mac': [
                'temurin'
        ],
        'arm32Linux'  : [
                'temurin'
        ]

]

// 03:30 Wed, Fri
triggerSchedule_nightly = 'TZ=UTC\n30 03 * * 3,5'
// 23:30 Sat
triggerSchedule_weekly = 'TZ=UTC\n30 23 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'hotspot'        : '',
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : ''
]

return this
