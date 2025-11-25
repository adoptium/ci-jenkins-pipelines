targetConfigurations = [
        'x64Mac'      : [
                'temurin',
                'hotspot'
        ],
        'x64Linux'    : [
                'temurin',
                'hotspot'
        ],
        'x64AlpineLinux' : [
                'temurin',
                'hotspot'
        ],
        'aarch64AlpineLinux' : [
                'temurin',
                'hotspot'
        ],
        'x64Windows'  : [
                'temurin',
                'hotspot'
        ],
        'aarch64Windows' : [
                'temurin',
                'hotspot'
        ],
        'ppc64Aix'    : [
                'temurin',
                'hotspot'
        ],
        'ppc64leLinux': [
                'temurin',
                'hotspot'
        ],
        's390xLinux'  : [
                'temurin',
                'hotspot'
        ],
        'aarch64Linux': [
                'hotspot',
                'temurin'
        ],
        'aarch64Mac': [
                'temurin',
                'hotspot'
        ],
        'riscv64Linux': [
                'temurin',
                'hotspot'
        ]
]

// 12:05 Sat - Weekend schedule for Oracle managed version that has no published tags
triggerSchedule_weekly  = 'TZ=UTC\n05 12 * * 6'

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'hotspot'        : '',
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : ''
]

return this
