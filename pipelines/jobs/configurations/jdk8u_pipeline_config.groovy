class Config8 {
  final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac        : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: [
                        hotspot : 'macos10.14',
                        corretto: 'build-macstadium-macos1010-1',
                        openj9  : 'macos10.14'
                ],
                test                 : 'default'
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
                        "hotspot"   : '--create-source-archive'
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
                        hotspot : '--jvm-variant client,server'
                ],
                test                 : 'default'
        ],

        ppc64Aix      : [
                os  : 'aix',
                arch: 'ppc64',
                additionalNodeLabels: [
                        hotspot: 'xlc13&&aix710',
                        openj9:  'xlc13&&aix715'
                ],
                test                 : 'default',
                cleanWorkspaceAfterBuild: true
        ],

        s390xLinux    : [
                os  : 'linux',
                arch: 's390x',
                test: [
                        hotspot: ['sanity.openjdk'],
                        openj9: 'default'
                ]
        ],

        sparcv9Solaris: [
                os  : 'solaris',
                arch: 'sparcv9',
                test: 'default'
        ],

        x64Solaris    : [
                os                  : 'solaris',
                arch                : 'x64',
                test                : 'default'
        ],

        ppc64leLinux  : [
                os  : 'linux',
                arch: 'ppc64le',
                additionalNodeLabels : 'centos7',
                test                 : 'default',
                configureArgs       : [
                        "openj9"      : '--enable-jitserver'
                ]
        ],

        arm32Linux    : [
                os  : 'linux',
                arch: 'arm',
                test: 'default'
        ],

        aarch64Linux  : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                dockerFile: [
                        dragonwell: 'pipelines/build/dockerFiles/dragonwell_aarch64.dockerfile'
                ],
                test                 : 'default',
                testDynamic          : false
        ],
  ]

}

Config8 config = new Config8()
return config.buildConfigurations
