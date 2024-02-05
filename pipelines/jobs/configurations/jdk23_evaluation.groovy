targetConfigurations = [
        'riscv64Linux': [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// Build tag driven beta builds now enabled

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this
