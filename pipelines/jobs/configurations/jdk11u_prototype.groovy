targetConfigurations = [
        'x64Mac'        : [
                'openj9'
        ],
        'x64Linux'      : [
                'openj9',
                'dragonwell',
                'corretto',
                'bisheng',
                'fast_startup'
        ],
        'x64Windows'    : [
                'openj9',
                'dragonwell'
        ],
        'ppc64Aix'      : [
                'openj9'
        ],
        'ppc64leLinux'  : [
                'openj9'
        ],
        's390xLinux'    : [
                'openj9'
        ],
        'aarch64Linux'  : [
                'openj9',
                'dragonwell',
                'bisheng'
        ],
        'aarch64Windows': [
                'temurin'
        ]
]

// 18:05 Tue, Thur
triggerSchedule_prototype = 'TZ=UTC\n05 18 * * 2,4'
// 17:05 Sat
triggerSchedule_weekly_prototype = 'TZ=UTC\n05 17 * * 6'

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'fast_startup'   : '',
        'bisheng'        : ''
]
return this
