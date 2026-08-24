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
        'x64Windows'  : [
                'temurin'
        ],
        'x32Windows'  : [
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
                'temurin'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
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
        'dragonwell'     : ''
]

disableJob = true

return this
