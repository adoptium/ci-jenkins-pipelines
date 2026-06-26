class Config16 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'macos10.14',
                configureArgs       : '--enable-dtrace'
        ],

        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage: [
                        hotspot     : 'adoptopenjdk/centos6_build_image',
                        openj9      : 'adoptopenjdk/centos7_build_image'
                ],
                dockerFile: [
                        openj9  : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                configureArgs       : [
                        'openj9'      : '--enable-dtrace --enable-jitserver',
                        'hotspot'     : '--enable-dtrace'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                configureArgs       : '--enable-headless-only=yes'
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                configureArgs       : '--enable-headless-only=yes'
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2012&&vs2017'
        ],

        x32Windows: [
                os                  : 'windows',
                arch                : 'x86-32',
                additionalNodeLabels: 'win2012&&vs2017',
                buildArgs : [
                        hotspot : '--jvm-variant client,server'
                ]
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64',
                additionalNodeLabels: [
                        hotspot: 'xlc16&&aix710',
                        openj9:  'xlc16&&aix715'
                ],
                cleanWorkspaceAfterBuild: true
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                configureArgs       : '--enable-dtrace'
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                additionalNodeLabels: 'centos7',
                configureArgs       : [
                        'hotspot'     : '--enable-dtrace',
                        'openj9'      : '--enable-dtrace --enable-jitserver'
                ]

        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                configureArgs       : '--enable-dtrace',
                testDynamic          : false
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                configureArgs       : '--enable-dtrace'
        ]
  ]

}

Config16 config = new Config16()
return config.buildConfigurations
