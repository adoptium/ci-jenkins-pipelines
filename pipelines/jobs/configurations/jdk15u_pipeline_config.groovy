class Config15 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'macos10.14',
                configureArgs       : '--enable-dtrace'
        ],

        x64MacXL    : [
                os                   : 'mac',
                arch                 : 'x64',
                additionalNodeLabels : 'macos10.14',
                additionalFileNameTag: 'macosXL',
                configureArgs        : '--with-noncompressedrefs --enable-dtrace'
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

        x64LinuxXL  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                dockerFile: [
                        openj9  : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                additionalFileNameTag: 'linuxXL',
                configureArgs       : '--with-noncompressedrefs --enable-dtrace --enable-jitserver'
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2012&&vs2017'
        ],

        x64WindowsXL: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2012&&vs2017',
                additionalFileNameTag: 'windowsXL',
                configureArgs        : '--with-noncompressedrefs'
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
                ]
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                configureArgs       : '--enable-dtrace'
        ],

        s390xLinuxXL  : [
                os                   : 'linux',
                arch                 : 's390x',
                additionalFileNameTag: 'linuxXL',
                configureArgs        : '--with-noncompressedrefs --enable-dtrace'
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

        ppc64leLinuxXL    : [
                os                   : 'linux',
                arch                 : 'ppc64le',
                additionalNodeLabels : 'centos7',
                additionalFileNameTag: 'linuxXL',
                configureArgs        : '--with-noncompressedrefs --disable-ccache --enable-dtrace'
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                configureArgs       : '--enable-dtrace'
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                configureArgs       : '--enable-dtrace'
        ],

        aarch64LinuxXL    : [
                os                   : 'linux',
                dockerImage          : 'adoptopenjdk/centos7_build_image',
                arch                 : 'aarch64',
                additionalFileNameTag: 'linuxXL',
                configureArgs        : '--with-noncompressedrefs --enable-dtrace'
        ]
  ]

}

Config15 config = new Config15()
return config.buildConfigurations
