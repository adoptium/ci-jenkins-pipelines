targetConfigurations = [
        'x64Mac'        : [    'temurin'],
        'x64Linux'      : [    'temurin'],
        'x64AlpineLinux': [    'temurin'],
        'x64Windows'    : [    'temurin'],
        'x32Windows'    : [    'temurin'],
        'ppc64Aix'      : [    'temurin'],
        'ppc64leLinux'  : [    'temurin'],
        's390xLinux'    : [    'temurin'],
        'aarch64Linux'  : [    'temurin'],
        'aarch64Mac'    : [    'temurin'],
        'arm32Linux'    : [    'temurin']
]

// 18:05 Tue, Thur
triggerSchedule_nightly = 'TZ=UTC\n05 18 * * 2,4'
// 17:05 Sat
triggerSchedule_weekly = 'TZ=UTC\n05 17 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'fast_startup'   : '',
        'bisheng'        : ''
]

return this
