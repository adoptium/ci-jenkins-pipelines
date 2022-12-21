targetConfigurations = [
        // 'aarch64Windows': [
        //         'temurin'
        // ],
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

// empty string as it wont get triggered now
triggerSchedule_pevaluation = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_evaluation = ''

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'bisheng'        : ''
]
return this
