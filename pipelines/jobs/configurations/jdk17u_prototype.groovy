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
triggerSchedule_prototype = ''
// empty string as it wont get triggered now
triggerSchedule_weekly_prototype = ''

// scmReferences to use for weekly prototype build
weekly_prototype_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'bisheng'        : ''
]
return this
