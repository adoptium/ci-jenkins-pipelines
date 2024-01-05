targetConfigurations = [
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'aarch64Windows': [
                'temurin'
        ],
        'riscv64Linux': [
                'temurin'
        ]
        // 'x64Mac'      : [
        //         'openj9'
        // ],
        // 'x64Linux'    : [
        //         'openj9',
        //         'bisheng'
        // ],
        // 'x64Windows'  : [
        //         'openj9'
        // ],
        // 'ppc64Aix'    : [
        //         'openj9'
        // ],
        // 'ppc64leLinux': [
        //         'openj9'
        // ],
        // 's390xLinux'  : [
        //         'openj9'
        // ],
        // 'aarch64Linux': [
        //         'openj9',
        //         'bisheng'
        // ]
]

// if set to empty string then it wont get triggered

// 23:30 Tue, Thu
triggerSchedule_evaluation = 'TZ=UTC\n30 23 * * 2,4'
// 12:05 Sun
triggerSchedule_weekly_evaluation = 'TZ=UTC\n05 12 * * 7'

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'bisheng'        : ''
]
return this
