targetConfigurations = [
        'x64Mac'        : [
                'temurin'
        ],
        'x64Linux'      : [
                'temurin'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'x32Windows'    : [
                'temurin'
        ],
        'x64Windows'    : [
                'temurin'
        ],
        'ppc64Aix'      : [
                'temurin'
        ],
        'ppc64leLinux'  : [
                'temurin'
        ],
        's390xLinux'    : [
                'temurin'
        ],
        'aarch64Linux'  : [
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'x64Solaris': [
                'temurin'
        ],
        'sparcv9Solaris': [
                'temurin'
        ]
]

// 18:05 Mon, Wed, Fri
triggerSchedule_nightly = 'TZ=UTC\n05 18 * * 1,3,5'
// 12:05 Sat
triggerSchedule_weekly = 'TZ=UTC\n05 12 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'bisheng'        : ''
]

return this
