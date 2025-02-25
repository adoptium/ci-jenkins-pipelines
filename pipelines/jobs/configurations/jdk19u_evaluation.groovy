targetConfigurations = [
        'aarch64AlpineLinux' : [
                'temurin'
        ]
        // 'aarch64Windows': [
        //        'temurin'
        //]
]

// scmReferences to use for weekly evaluation build
weekly_evaluation_scmReferences = [
        'temurin'        : ''
]
return this
