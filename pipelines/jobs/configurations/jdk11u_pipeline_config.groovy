class Config11 {
  final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels : 'macos10.14',
                test                : 'default',
                configureArgs       : [
                        "openj9"      : '--enable-dtrace=auto --with-cmake',
                        "temurin"     : '--enable-dtrace=auto'
                ]
        ],

        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/centos6_build_image',
                dockerFile: [
                        openj9  : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                test                : [
                        nightly: ["sanity.openjdk", "sanity.system", "extended.system", "sanity.perf", "sanity.functional", "extended.functional"],
                        weekly : ["extended.openjdk", "extended.perf", "special.functional", "sanity.external"]
                ],
                configureArgs       : [
                        "openj9"      : '--enable-jitserver --enable-dtrace=auto',
                        "temurin"     : '--enable-dtrace=auto',
                        "corretto"    : '--enable-dtrace=auto',
                        "SapMachine"  : '--enable-dtrace=auto',
                        "dragonwell"  : '--enable-dtrace=auto --enable-unlimited-crypto --with-jvm-variants=server --with-zlib=system --with-jvm-features=zgc',
                        "fast_startup": '--enable-dtrace=auto',
                        "bisheng"     : '--enable-dtrace=auto --with-extra-cflags=-fstack-protector-strong --with-extra-cxxflags=-fstack-protector-strong --with-jvm-variants=server --disable-warnings-as-errors'
                ],
                buildArgs            : [
                        "temurin"     : '--create-source-archive'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes'
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes'
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: [
                        temurin:    'win2012&&vs2019',
                        openj9:     'win2012&&vs2017',
                        dragonwell: 'win2012'
                ],
                buildArgs : [
                        temurin : '--jvm-variant client,server'
                ],
                test                : 'default'
        ],

        x32Windows: [
                os                  : 'windows',
                arch                : 'x86-32',
                additionalNodeLabels: 'win2012&&vs2019',
                buildArgs : [
                        temurin : '--jvm-variant client,server'
                ],
                test                : 'default'
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64',
                additionalNodeLabels: [
                        temurin: 'xlc16&&aix710',
                        openj9:  'xlc16&&aix715'
                ],
                test                : 'default',
                cleanWorkspaceAfterBuild: true
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                test                : 'default',
                configureArgs       : '--enable-dtrace=auto'
        ],

        sparcv9Solaris    : [
                os                  : 'solaris',
                arch                : 'sparcv9',
                test                : false,
                configureArgs       : '--enable-dtrace=auto'
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                additionalNodeLabels : 'centos7',
                test                : 'default',
                configureArgs       : [
                        "temurin"     : '--enable-dtrace=auto',
                        "openj9"      : '--enable-dtrace=auto --enable-jitserver'
                ]

        ],

        aarch64Mac: [
                os                  : 'mac',
                arch                : 'aarch64',
                additionalNodeLabels: 'macos11',
                test                : 'default',
                configureArgs       : '--disable-ccache'
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                test                : 'default',
                configureArgs       : '--enable-dtrace=auto'
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                additionalNodeLabels: [
                        dragonwell: 'armv8.2'
                ],
                additionalTestLabels: [
                        dragonwell: 'armv8.2'
                ],
                configureArgs       : [
                        "temurin"   : '--enable-dtrace=auto',
                        "openj9"    : '--enable-dtrace=auto',
                        "corretto"  : '--enable-dtrace=auto',
                        "dragonwell": "--enable-dtrace=auto --with-extra-cflags=\"-march=armv8.2-a+crypto\" --with-extra-cxxflags=\"-march=armv8.2-a+crypto\"",
                        "bisheng"   : '--enable-dtrace=auto --with-extra-cflags=-fstack-protector-strong --with-extra-cxxflags=-fstack-protector-strong --with-jvm-variants=server'
                ],
                testDynamic        : false
        ],

        riscv64Linux      :  [
                os                   : 'linux',
                dockerImage          : [
                        "openj9"     : 'adoptopenjdk/centos6_build_image',
                        "bisheng"    : 'adoptopenjdk/centos6_build_image'
                ],
                arch                 : 'riscv64',
                crossCompile         : [
                        "openj9"     : 'x64',
                        "bisheng"    : 'x64'
                ],
                buildArgs            : [
                        "openj9"     : '--cross-compile',
                        "bisheng"    : '--cross-compile --branch risc-v'
                ],
                configureArgs        : [
                        "openj9"     : '--disable-ddr --openjdk-target=riscv64-unknown-linux-gnu --with-sysroot=/opt/fedora28_riscv_root',
                        "bisheng"    : '--openjdk-target=riscv64-unknown-linux-gnu --with-sysroot=/opt/fedora28_riscv_root --with-jvm-features=shenandoahgc'
                ],
                test                : [
                        nightly: ['sanity.openjdk'],
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf']
                ]
        ]
  ]

}

Config11 config = new Config11()
return config.buildConfigurations
