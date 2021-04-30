targetConfigurations = [
        "x64Mac"        : [
                "hotspot",
                "openj9"
        ],
        "x64Linux"      : [
                "hotspot",
                "openj9",
                "corretto",
                "dragonwell"
        ],
        "x32Windows"    : [
                "hotspot",
                "openj9"
        ],
        "x64Windows"    : [
                "hotspot",
                "openj9",
                "dragonwell"
        ],
        "ppc64Aix"      : [
                "hotspot",
                "openj9"
        ],
        "ppc64leLinux"  : [
                "hotspot",
                "openj9"
        ],
        "s390xLinux"    : [
                "hotspot",
                "openj9"
        ],
        "aarch64Linux"  : [
                "hotspot",
                "openj9",
                "dragonwell"
        ],
        "arm32Linux"  : [
                "hotspot"
        ]
]

// 18:05 Mon, Wed, Fri
triggerSchedule_nightly="TZ=UTC\n05 18 * * 1,3,5"
// 12:05 Sat
triggerSchedule_weekly="TZ=UTC\n05 12 * * 6"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "hotspot"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : ""
]

return this
