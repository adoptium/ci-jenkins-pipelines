targetConfigurations = [
        'aarch64AlpineLinux' : [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this