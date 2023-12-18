targetConfigurations = [
        'riscv64Linux': [
                'temurin'
        ],
        'aarch64Windows' : [
                'temurin'
        ]
]

// if set to empty string then it wont get triggered

// scmReferences to use for weekly evaluation release build
weekly_evaluation_scmReferences = [
        'hotspot'        : '',
        'temurin'        : ''
]

return this
