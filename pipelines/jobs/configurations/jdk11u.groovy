targetConfigurations = [
        'x64Mac'        : [    'temurin',    'openj9'                    ],
        'x64Linux'      : [    'temurin',    'hotspot',    'dragonwell',    'corretto',    'bisheng',    'fast_startup'],
        'x64AlpineLinux': [    'temurin'                            ],
        'x64Windows'    : [    'temurin',    'openj9',    'dragonwell'            ],
        'ppc64Aix'      : [    'temurin',    'hotspot'                   ],
        'ppc64leLinux'  : [    'temurin',    'openj9'                    ],
        's390xLinux'    : [    'temurin',    'hotspot'                   ],
        'aarch64Linux'  : [    'temurin',    'openj9',    'dragonwell',                   'bisheng'    ],
        'aarch64Mac'    : [    'temurin',                           ],
        'arm32Linux'    : [    'temurin'                            ]
]

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
