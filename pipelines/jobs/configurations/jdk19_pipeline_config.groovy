class Config19 {
  final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'macos10.14',
                additionalTestLabels: [
                        openj9      : '!sw.os.osx.10_11'
                ],
                test                : 'default',
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage: [
                        temurin     : 'adoptopenjdk/centos6_build_image',
                        openj9      : 'adoptopenjdk/centos7_build_image'
                ],
                dockerFile: [
                        openj9      : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                test                : 'default',
                additionalTestLabels: [
                        openj9      : '!(centos6||rhel6)'
                ],
                configureArgs       : [
                        "openj9"    : '--enable-dtrace --enable-jitserver',
                        "temurin"   : '--enable-dtrace'
                ],
                buildArgs           : [
                        "temurin"   : '--create-source-archive --create-jre-image --create-sbom'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],
        
        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2012&&vs2019',
                test                : 'default',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        // TODO: Enable testing (https://github.com/adoptium/ci-jenkins-pipelines/issues/77)
        aarch64Windows: [
                os                  : 'windows',
                arch                : 'aarch64',
                crossCompile        : 'x64',
                additionalNodeLabels: 'win2016&&vs2019',
                test                : [
                        nightly: [],
                        weekly : []
                ],
                buildArgs       : [
                        "openj9"    : '--cross-compile',
                        "temurin"   : '--create-jre-image --create-sbom --cross-compile'
                ]

        ],


        x32Windows: [
                os                  : 'windows',
                arch                : 'x86-32',
                additionalNodeLabels: 'win2012&&vs2019',
                test                : 'default',
                buildArgs           : [
                        "temurin"   : '--jvm-variant client,server --create-jre-image --create-sbom'
                ]
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64',
                additionalNodeLabels: [
                        temurin: 'xlc16&&aix720',
                        openj9:  'xlc16&&aix715'
                ],
                test                : 'default',
                additionalTestLabels: [
                        temurin      : 'aix720'
                ],
                cleanWorkspaceAfterBuild: true,
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],


        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                test                : 'default',
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                additionalNodeLabels: 'centos7',
                test                : 'default',
                configureArgs       : [
                        "temurin"     : '--enable-dtrace',
                        "openj9"      : '--enable-dtrace --enable-jitserver'
                ],
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                configureArgs : '--enable-dtrace',
                testDynamic          : false,
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],
        
        aarch64Mac: [
                os                  : 'mac',
                arch                : 'aarch64',
                additionalNodeLabels: 'macos11',
                test                : 'default',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                crossCompile        : 'aarch64',
                dockerImage         : 'adoptopenjdk/ubuntu1604_build_image',
                dockerArgs          : '--platform linux/arm/v7',
                test                : 'default',
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        "temurin"   : '--create-jre-image --create-sbom'
                ]
        ],
        riscv64Linux      :  [
                os                   : 'linux',
                arch                 : 'riscv64',
                configureArgs        : '--enable-dtrace --with-native-debug-symbols=none',
                buildArgs            : '-r https://github.com/openjdk/riscv-port -b riscv-port --custom-cacerts false --disable-adopt-branch-safety --create-sbom',
                test                : [
                        nightly: ['sanity.openjdk'],
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf']
                ]
        ]

  ]

}

Config19 config = new Config19()
return config.buildConfigurations
