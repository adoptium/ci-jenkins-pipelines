targetConfigurations = [
        'x64Mac'      : [
                'temurin',
                'openj9'
        ],
        'x64Linux'    : [
                'temurin',
                'hotspot'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'x64Windows'  : [
                'temurin',
                'openj9'
        ],
        'ppc64Aix'    : [
                'temurin',
                'hotspot'
        ],
        'ppc64leLinux': [
                'temurin',
                'openj9'
        ],
        's390xLinux'  : [
                'temurin',
                'hotspot'
        ],
        'aarch64Linux': [
                'temurin',
                'openj9',
                'bisheng'
        ],
        'aarch64Mac': [
                'temurin'
        ],
        'arm32Linux'  : [
                'temurin'
        ],
        'riscv64Linux': [
                'temurin'
        ]
]

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'temurin'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : '',
        'bisheng'        : ''
]

return this
