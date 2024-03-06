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
        'x64Windows'  : [
                'hotspot',
                'fast_debug'
        ],
        'x32Windows'  : [
                'hotspot'
        ],
        'ppc64leLinux': [
                'hotspot'
        ],
        'ppc64Aix'    : [
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
        ]
]

// scmReferences to use for weekly trestle build
weekly_trestle_scmReferences = [
        'hotspot'        : '',
        'fast_debug'     : ''
]

return this
