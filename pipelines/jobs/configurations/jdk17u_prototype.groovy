targetConfigurations = [
        'aarch64Windows': [
                'temurin'
        ],
        'x64Mac'      : [
                'openj9'
        ],
        'x64Linux'    : [
                'openj9',
                'bisheng'
        ],
        'x64Windows'  : [
                'openj9'
        ],
        'ppc64Aix'    : [
                'openj9'
        ],
        'ppc64leLinux': [
                'openj9'
        ],
        's390xLinux'  : [
                'openj9'
        ],
        'aarch64Linux': [
                'openj9',
                'bisheng'
        ]
]

// 23:30 Tue, Thur
triggerSchedule_prototype = 'TZ=UTC\n30 23 * * 2,4'
// 12:05 Sun
triggerSchedule_weekly_prototype = 'TZ=UTC\n05 12 * * 7'

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'bisheng'        : ''
]
return this
