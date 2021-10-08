targetConfigurations = [
        "x64Mac"        : [    "hotspot",    "openj9"                    ],
        "x64Linux"      : [    "hotspot",    "openj9",    "dragonwell",    "corretto",    "bisheng"    ],
        "x64AlpineLinux": [    "hotspot"                            ],
        "x64Windows"    : [    "hotspot",    "openj9",    "dragonwell"            ],
        "x32Windows"    : [    "hotspot"                            ],
        "ppc64Aix"      : [    "hotspot",    "openj9"                    ],
        "ppc64leLinux"  : [    "hotspot",    "openj9"                    ],
        "s390xLinux"    : [    "hotspot",    "openj9"                    ],
        "aarch64Linux"  : [    "hotspot",    "openj9",    "dragonwell",                   "bisheng"    ],
        "arm32Linux"    : [    "hotspot"                            ],
        "riscv64Linux"  : [                  "openj9",                                    "bisheng"    ]
]

// 18:05 Tue, Thur
triggerSchedule_nightly="TZ=UTC\n05 18 * * 2,4"
// 17:05 Sat
triggerSchedule_weekly="TZ=UTC\n05 17 * * 6"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "hotspot"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : "",
        "bisheng"        : ""
]

return this
