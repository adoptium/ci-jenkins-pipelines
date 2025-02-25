targetConfigurations = [
        'x64Mac'      : [
                'hotspot',
                'openj9'
        ],
        'x64Linux'    : [
                'hotspot',
                'openj9'
        ],
        'x64AlpineLinux' : [
                'hotspot'
        ],
        'x64Windows'  : [
                'hotspot',
                'openj9'
        ],
        'x32Windows'  : [
                'hotspot'
        ],
        'ppc64Aix'    : [
                'hotspot',
                'openj9'
        ],
        'ppc64leLinux': [
                'hotspot',
                'openj9'
        ],
        's390xLinux'  : [
                'hotspot',
                'openj9'
        ],
        'aarch64Linux': [
                'hotspot',
                'openj9'
        ],
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'arm32Linux'  : [
                'hotspot'
        ]
]

// scmReferences to use for weekly release build
weekly_release_scmReferences = [
        'hotspot'        : '',
        'openj9'         : '',
        'corretto'       : '',
        'dragonwell'     : ''
]
disableJob = true

return this
