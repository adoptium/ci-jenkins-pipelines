targetConfigurations = [
        'x64Mac'      : [
                'temurin',
                'openj9'
        ],
        'x64Linux'    : [
                'temurin',
                'openj9',
                'bisheng'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'x64Windows'  : [
                'temurin',
                'openj9'
        ],
        'x32Windows'  : [
                'temurin'
        ],
        'ppc64Aix'    : [
                'temurin',
                'openj9'
        ],
        'ppc64leLinux': [
                'temurin',
                'openj9'
        ],
        's390xLinux'  : [
                'temurin',
                'openj9'
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
