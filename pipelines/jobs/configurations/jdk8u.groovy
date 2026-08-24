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
        'x32Windows'    : [
                'hotspot'
        ],
        'x64Windows'    : [
                'temurin',
                'hotspot'
        ],
        'ppc64Aix'      : [
                'temurin',
                'hotspot'
        ],
        'ppc64leLinux'  : [
                'temurin',
                'hotspot'
        ],
        's390xLinux'    : [
                'openj9'
        ],
        'aarch64Linux'  : [
                'temurin',
                'hotspot'
        ],
        'arm32Linux'  : [
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
