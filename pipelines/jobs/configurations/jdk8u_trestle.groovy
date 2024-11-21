targetConfigurations = [
        'x64Mac'        : [
                'hotspot',
                'fast_debug'
        ],
        'x64Linux'      : [
                'hotspot',
                'fast_debug'
        ],
        'x64AlpineLinux' : [
                'hotspot'
        ],
        'x32Windows'    : [
                'hotspot'
        ],
        'x64Windows'    : [
                'hotspot',
                'fast_debug'
        ],
        'ppc64Aix'      : [
                'hotspot'
        ],
        'ppc64leLinux'  : [
                'hotspot'
        ],
        'aarch64Linux'  : [
                'hotspot',
                'fast_debug'
        ],
        'arm32Linux'  : [
                'hotspot'
        ],
        'x64Solaris': [
                'hotspot'
        ],
        'sparcv9Solaris': [
                'hotspot'
        ]
]

// scmReferences to use for weekly trestle build
weekly_trestle_scmReferences = [
        'hotspot'        : '',
        'fast_debug'     : ''
]

return this
