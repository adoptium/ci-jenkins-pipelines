targetConfigurations = [
        "x64Mac"        : [
                "temurin",
                "openj9"
        ],
        "x64Linux"      : [
                "temurin",
                "openj9",
                "corretto",
                "dragonwell",
                "bisheng"
        ],
        "x64AlpineLinux" : [
                "hotspot"
        ],
        "x32Windows"    : [
                "temurin",
                "openj9"
        ],
        "x64Windows"    : [
                "temurin",
                "openj9",
                "dragonwell"
        ],
        "ppc64Aix"      : [
                "temurin",
                "openj9"
        ],
        "ppc64leLinux"  : [
                "temurin",
                "openj9"
        ],
        "s390xLinux"    : [
                "temurin",
                "openj9"
        ],
        "aarch64Linux"  : [
                "temurin",
                "openj9",
                "dragonwell",
                "bisheng"
        ],
        "arm32Linux"  : [
                "temurin"
        ],
        "x64Solaris": [
                "temurin"
        ],
        "sparcv9Solaris": [
                "temurin"
        ]
]

// 18:05 Mon, Wed, Fri
triggerSchedule_nightly="TZ=UTC\n05 18 * * 1,3,5"
// 12:05 Sat
triggerSchedule_weekly="TZ=UTC\n05 12 * * 6"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "temurin"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : "",
        "bisheng"        : ""
]

return this
