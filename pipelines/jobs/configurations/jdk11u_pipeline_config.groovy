class Config11 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                test                : 'default',
                additionalNodeLabels: 'xcode15.0.1',
                configureArgs       : [
                        'openj9'      : '--enable-dtrace=auto --with-cmake',
                        'temurin'     : '--enable-dtrace=auto'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/centos6_build_image',
                dockerFile: [
                        openj9  : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                test: [
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.functional', 'extended.functional', 'extended.openjdk', 'extended.perf', 'special.functional', 'sanity.external', 'dev.openjdk', 'dev.functional']
                ],
                configureArgs       : [
                        'openj9'      : '--enable-dtrace=auto',
                        'temurin'     : '--enable-dtrace=auto',
                        'corretto'    : '--enable-dtrace=auto',
                        'SapMachine'  : '--enable-dtrace=auto',
                        'dragonwell'  : '--enable-dtrace=auto --enable-unlimited-crypto --with-jvm-variants=server --with-zlib=system --with-jvm-features=zgc',
                        'fast_startup': '--enable-dtrace=auto',
                        'bisheng'     : '--enable-dtrace=auto --with-extra-cflags=-fstack-protector-strong --with-extra-cxxflags=-fstack-protector-strong --with-jvm-variants=server --disable-warnings-as-errors'
                ],
                buildArgs            : [
                        'temurin'     : '--create-source-archive --create-sbom --enable-sbom-strace'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : [
                        'openj9'    : '--enable-headless-only=yes',
                        'temurin'   : '--enable-headless-only=yes --with-jobs=16'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: [
                        temurin:    'win2022&&vs2019',
                        openj9:     'win2012&&vs2017',
                        dragonwell: 'win2012'
                ],
                buildArgs : [
                        temurin : '--jvm-variant client,server --create-sbom'
                ],
                test                : 'default'
        ],

        x32Windows: [
                os                  : 'windows',
                arch                : 'x86-32',
                additionalNodeLabels: 'win2022&&vs2019',
                buildArgs : [
                        temurin : '--jvm-variant client,server --create-sbom'
                ],
                test                : 'default'
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64',
                additionalNodeLabels: 'xlc13&&aix720',
                test                : 'default',
                additionalTestLabels: 'sw.os.aix.7_2',
                cleanWorkspaceAfterBuild: true,
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                dockerImage         : 'rhel7_build_image',
                test                : 'default',
                configureArgs       : '--enable-dtrace=auto',
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        sparcv9Solaris    : [
                os                  : 'solaris',
                arch                : 'sparcv9',
                test                : false,
                configureArgs       : '--enable-dtrace=auto',
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                configureArgs       : [
                        'temurin'     : '--enable-dtrace=auto',
                        'openj9'      : '--enable-dtrace=auto'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]

        ],

        aarch64Mac: [
                os                  : 'mac',
                arch                : 'aarch64',
                test                : 'default',
                additionalNodeLabels: 'xcode15.0.1',
                configureArgs       : '--disable-ccache',
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                crossCompile        : 'aarch64',
                dockerImage         : 'adoptopenjdk/ubuntu1604_build_image',
                dockerArgs          : '--platform linux/arm/v7',
                test                : 'default',
                configureArgs       : [
                        'openj9'    : '--enable-dtrace=auto',
                        'temurin'   : '--enable-dtrace=auto --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
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
                        'temurin'   : '--enable-dtrace=auto --with-jobs=16',
                        'openj9'    : '--enable-dtrace=auto',
                        'corretto'  : '--enable-dtrace=auto',
                        'dragonwell': "--enable-dtrace=auto --with-extra-cflags=\"-march=armv8.2-a+crypto\" --with-extra-cxxflags=\"-march=armv8.2-a+crypto\"",
                        'bisheng'   : '--enable-dtrace=auto --with-extra-cflags=-fstack-protector-strong --with-extra-cxxflags=-fstack-protector-strong --with-jvm-variants=server'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        riscv64Linux      :  [
                os                   : 'linux',
                arch                 : 'riscv64',
                dockerImage          : [
                        'temurin'    : 'adoptopenjdk/ubuntu2004_build_image:linux-riscv64',
                        'openj9'     : 'adoptopenjdk/centos6_build_image',
                        'bisheng'    : 'adoptopenjdk/centos6_build_image'
                ],
                dockerArgs           : [
                        'temurin'    : '--platform linux/riscv64'
                ],
                crossCompile         : [
                        'temurin'    : 'qemustatic',
                        'openj9'     : 'x64',
                        'bisheng'    : 'x64'
                ],
                buildArgs            : [
                        'temurin'    : '--create-sbom',
                        'openj9'     : '--cross-compile',
                        'bisheng'    : '--cross-compile --branch risc-v'
                ],
                configureArgs        : [
                        'temurin'    : '--enable-headless-only=yes --enable-dtrace --disable-ccache',
                        'openj9'     : '--disable-ddr --openjdk-target=riscv64-unknown-linux-gnu --with-sysroot=/opt/fedora28_riscv_root',
                        'bisheng'    : '--openjdk-target=riscv64-unknown-linux-gnu --with-sysroot=/opt/fedora28_riscv_root --with-jvm-features=shenandoahgc'
                ],
                test                : [
                        'temurin'   : 'default',
                        'openj9'    : [
                                nightly: ['sanity.openjdk'],
                                weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf']
                        ],
                        'bisheng'   : [
                                nightly: ['sanity.openjdk'],
                                weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf']
                        ]
                ],
        ],

        aarch64Windows: [
                os                  : 'windows',
                arch                : 'aarch64',
                crossCompile        : 'x64',
                additionalNodeLabels: 'win2022&&vs2019',
                test                : 'default',
                buildArgs       : [
                        'temurin'   : '--jvm-variant client,server --create-sbom --cross-compile'
                ]
        ]
  ]

}

Config11 config = new Config11()
return config.buildConfigurations
