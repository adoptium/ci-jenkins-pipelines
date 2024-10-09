targetConfigurations = [
        'x64Mac'      : [
                'temurin'
        ],
        'x64Linux'    : [
                'temurin'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'x64Windows'  : [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ],
        'ppc64Aix'    : [
                'temurin'
        ],
        'ppc64leLinux': [
                'temurin'
        ],
        's390xLinux'  : [
                'temurin'
        ],
        'aarch64Linux': [
                'hotspot',
                'temurin'
        ],
        'aarch64Mac': [
                'temurin'
        ],
        'riscv64Linux': [
                'temurin'
        ]
]

// 12:05 Sat - Weekend schedule for when Oracle managed jdk22u.groovy version is created
triggerSchedule_weekly  = 'TZ=UTC\n05 12 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'hotspot'        : '',
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : ''
]

disableJob = true

return this
