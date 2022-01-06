targetConfigurations = [
        "x64Mac"      : [
                "hotspot"
        ],
        "x64Linux"    : [
                "hotspot"
        ],
        "x64AlpineLinux" : [
                "hotspot"
        ],
        "x64Windows"  : [
                "hotspot"
        ],
        "x32Windows"  : [
                "hotspot"
        ],
        "ppc64Aix"    : [
                "hotspot"
        ],
        "ppc64leLinux": [
                "hotspot"
        ],
        "s390xLinux"  : [
                "hotspot"
        ],
        "aarch64Linux": [
                "hotspot"
        ],
        "aarch64Mac": [
                "hotspot"
        ],
        "arm32Linux"  : [
                "hotspot"
        ],
        "riscv64Linux"  : [
                "hotspot"
        ]

]

// 03:30 Tue, Thur, Sat
triggerSchedule_nightly="TZ=UTC\n30 03 * * 2,4,6"
// 17:05 Sun
triggerSchedule_weekly="TZ=UTC\n05 17 * * 7"

// scmReferences to use for weekly release build
weekly_release_scmReferences=[
        "hotspot"        : "",
        "openj9"         : "",
        "corretto"       : "",
        "dragonwell"     : ""
]

return this
