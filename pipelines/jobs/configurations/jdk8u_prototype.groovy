targetConfigurations = [
        'x64Mac'        : [
                'openj9'
        ],
        'x64Linux'      : [
                'openj9',
                'corretto',
                'dragonwell',
                'bisheng'
        ],
        'x32Windows'    : [
                'openj9'
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
        ]
]

// 18:05 Mon, Wed, Fri
triggerSchedule_prototype = 'TZ=UTC\n05 18 * * 1,3,5'
// 12:05 Sat
triggerSchedule_weekly_prototype = 'TZ=UTC\n05 12 * * 6'

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'bisheng'        : ''
]
return this
