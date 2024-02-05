targetConfigurations = [
        'aarch64AlpineLinux' : [
                'temurin'
        ]
        // 'x64Mac'        : [
        //         'openj9'
        // ],
        // 'x64Linux'      : [
        //         'openj9',
        //         'corretto',
        //         'dragonwell',
        //         'bisheng'
        // ],
        // 'x32Windows'    : [
        //         'openj9'
        // ],
        // 'x64Windows'    : [
        //         'openj9',
        //         'dragonwell'
        // ],
        // 'ppc64Aix'      : [
        //         'openj9'
        // ],
        // 'ppc64leLinux'  : [
        //         'openj9'
        // ],
        // 's390xLinux'    : [
        //         'openj9'
        // ],
        // 'aarch64Linux'  : [
        //         'openj9',
        //         'dragonwell',
        //         'bisheng'
        // ]
]

// Build tag driven beta builds now enabled
// if set to empty string then it wont get triggered
//triggerSchedule_evaluation = 'TZ=UTC\n05 18 * * 1,3,5'
// if set to empty string then it wont get triggered
//triggerSchedule_weekly_evaluation = 'TZ=UTC\n05 12 * * 6'

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'bisheng'        : ''
]
return this
