class Config8 {
  final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac        : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: [
                        temurin : 'macos10.14',
                        corretto: 'build-macstadium-macos1010-1',
                        openj9  : 'macos10.14'
                ],
                test                 : 'default',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        x64Linux      : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/centos6_build_image',
                dockerFile: [
                        openj9  : 'pipelines/build/dockerFiles/cuda.dockerfile',
                        dragonwell: 'pipelines/build/dockerFiles/dragonwell.dockerfile'
                ],
                test                 : 'default',
                configureArgs       : [
                        "openj9"      : '--enable-jitserver',
                        "dragonwell"  : '--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system',
                ],
                buildArgs           : [
                        "temurin"   : '--create-source-archive --create-sbom'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--disable-headful',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--disable-headful',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        x64Windows    : [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2012',
                test                 : 'default'
        ],

        x32Windows    : [
                os                  : 'windows',
                arch                : 'x86-32',
                additionalNodeLabels: 'win2012',
                buildArgs : [
                        temurin : '--jvm-variant client,server'
                ],
                test                 : 'default'
        ],

        ppc64Aix      : [
                os  : 'aix',
                arch: 'ppc64',
                additionalNodeLabels: [
                        temurin: 'xlc13&&aix710',
                        openj9:  'xlc13&&aix715'
                ],
                test                 : 'default',
                cleanWorkspaceAfterBuild: true,
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        s390xLinux    : [
                os  : 'linux',
                arch: 's390x',
                test: [
                        temurin: ['sanity.openjdk'],
                        openj9: 'default'
                ],
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        sparcv9Solaris: [
                os  : 'solaris',
                arch: 'sparcv9',
                test: 'default',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        x64Solaris    : [
                os                  : 'solaris',
                arch                : 'x64',
                test                : 'default',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        ppc64leLinux  : [
                os  : 'linux',
                arch: 'ppc64le',
                additionalNodeLabels : 'centos7',
                test                 : 'default',
                configureArgs       : [
                        "openj9"      : '--enable-jitserver'
                ],
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        arm32Linux    : [
                os  : 'linux',
                arch: 'arm',
                test: 'default',
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],

        aarch64Linux  : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                dockerFile: [
                        dragonwell: 'pipelines/build/dockerFiles/dragonwell_aarch64.dockerfile'
                ],
                test                 : 'default',
                testDynamic          : false,
                buildArgs           : [
                        "temurin"   : '--create-sbom'
                ]
        ],
  ]

}

Config8 config = new Config8()
return config.buildConfigurations
