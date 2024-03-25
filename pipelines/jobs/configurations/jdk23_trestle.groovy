targetConfigurations = [
        'x64Mac'      : [
                'hotspot',
                'fast_debug'
        ],
        'x64Linux'    : [
                'hotspot',
                'fast_debug'
        ],
        'x64AlpineLinux' : [
                'hotspot'
        ],
        'aarch64AlpineLinux' : [
                'hotspot'
        ],
        'x64Windows'  : [
                'hotspot',
                'fast_debug'
        ],
        'ppc64Aix'    : [
                'hotspot'
        ],
        'ppc64leLinux': [
                'hotspot'
        ],
        's390xLinux'  : [
                'hotspot'
        ],
        'aarch64Linux': [
                'hotspot',
                'fast_debug'
        ],
        'aarch64Mac': [
                'hotspot',
                'fast_debug'
        ],
        'arm32Linux'  : [
                'hotspot'
        ],
        'x32Windows'  : [
                'hotspot'
        ]
]

// scmReferences to use for weekly trestle build
weekly_trestle_scmReferences = [
        'hotspot'        : '',
        'fast_debug'     : ''
]

return this
