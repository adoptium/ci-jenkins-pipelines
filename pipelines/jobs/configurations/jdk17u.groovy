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
        'x64Windows'  : [
                'temurin',
                'hotspot'
        ],
        'x32Windows'  : [
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
                'temurin',
                'hotspot'
        ],
        'aarch64Mac': [
                'temurin',
                'hotspot'
        ],
        'arm32Linux'  : [
                'temurin',
                'hotspot'
        ],
        'riscv64Linux': [
                'temurin',
                'hotspot'
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
