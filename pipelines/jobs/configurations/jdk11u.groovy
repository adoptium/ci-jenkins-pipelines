targetConfigurations = [
        "x64Mac"        : [    "temurin",    "openj9"                    ],
        "x64Linux"      : [    "temurin",    "openj9",    "dragonwell",    "corretto",    "bisheng"    ],
        "x64AlpineLinux": [    "temurin"                            ],
        "x64Windows"    : [    "temurin",    "openj9",    "dragonwell"            ],
        "x32Windows"    : [    "temurin"                            ],
        "ppc64Aix"      : [    "temurin",    "openj9"                    ],
        "ppc64leLinux"  : [    "temurin",    "openj9"                    ],
        "s390xLinux"    : [    "temurin",    "openj9"                    ],
        "aarch64Linux"  : [    "temurin",    "openj9",    "dragonwell",                   "bisheng"    ],
        "arm32Linux"    : [    "temurin"                            ],
        "riscv64Linux"  : [                  "openj9",                                    "bisheng"    ]
]

// 18:05 Tue, Thur
triggerSchedule_nightly="TZ=UTC\n05 18 * * 2,4"
// 17:05 Sat
triggerSchedule_weekly="TZ=UTC\n05 17 * * 6"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "temurin"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : "",
        "bisheng"        : ""
]

return this
