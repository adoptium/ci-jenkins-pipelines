// when no target for evaluation, set it to null and disable scheduler below
targetConfigurations = [
        'aarch64Windows': [
                'temurin'
        ],
        'riscv64Linux': [
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

// 11:30 Tue, Thu
triggerSchedule_evaluation = 'TZ=UTC\n30 11 * * 2,4'
// 23:05 Sun
triggerSchedule_weekly_evaluation = 'TZ=UTC\n05 23 * * 7'

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'fast_startup'   : '',
        'bisheng'        : ''
]
return this
