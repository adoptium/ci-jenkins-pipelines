targetConfigurations = [
        'x64Mac'        : [
                'temurin'
        ],
        'x64Linux'      : [
                'temurin',
                'openj9',
                'corretto',
                'dragonwell',
                'bisheng',
                'hotspot'
        ],
        'x64AlpineLinux' : [
                'temurin'
        ],
        'x64Windows'    : [
                'temurin',
                'openj9',
                'dragonwell'
        ],
        'ppc64Aix'      : [
                'temurin',
                'hotspot'
        ],
        'ppc64leLinux'  : [
                'temurin',
                'openj9'
        ],
        's390xLinux'    : [
                'openj9'
        ],
        'aarch64Linux'  : [
                'temurin',
                'openj9',
                'dragonwell',
                'bisheng'
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
