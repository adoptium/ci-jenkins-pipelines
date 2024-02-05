// when no target for evaluation, set it to null and disable scheduler below
targetConfigurations = [
        'aarch64Windows': [
                'temurin'
        ]
        // 'x64Mac'        : [
        //         'openj9'
        // ],
        // 'x64Linux'      : [
        //         'openj9',
        //         'dragonwell',
        //         'corretto',
        //         'bisheng',
        //         'fast_startup'
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
//triggerSchedule_evaluation = ''
// if set to empty string then it wont get triggered
//triggerSchedule_weekly_evaluation= ''

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'fast_startup'   : '',
        'bisheng'        : ''
]
return this
