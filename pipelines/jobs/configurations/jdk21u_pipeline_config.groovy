class Config21 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'xcode15.0.1',
                additionalTestLabels: [
                        openj9      : '!sw.os.osx.10_11'
                ],
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                dockerFile: [
                        openj9      : 'pipelines/build/dockerFiles/cuda.dockerfile'
                ],
                test: [
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.functional', 'extended.functional', 'extended.openjdk', 'extended.perf', 'special.functional', 'sanity.external', 'dev.openjdk', 'dev.functional', 'dev.system']
                ],
                reproducibleCompare : [
                        'temurin'   : true
                ],
                additionalTestLabels: [
                        openj9      : '!(centos6||rhel6)'
                ],
                configureArgs       : [
                        'openj9'    : '--enable-dtrace',
                        'temurin'   : '--enable-dtrace'
                ],
                buildArgs           : [
                        'temurin'   : '--create-source-archive --create-jre-image --create-sbom --enable-sbom-strace --use-adoptium-devkit gcc-11.3.0-Centos7.9.2009-b02'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: 'win2022&&vs2022',
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                configureArgs       : "--with-ucrt-dll-dir='C:/progra~2/wi3cf2~1/10/Redist/10.0.22621.0/ucrt/DLLs/x64'",
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
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
                        temurin      : 'sw.os.aix.7_2'
                ],
                cleanWorkspaceAfterBuild: true,
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                dockerImage         : 'rhel7_build_image',
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --use-adoptium-devkit s390x-on-s390x.RH7'
                ]
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                configureArgs       : [
                        'openj9'      : '--enable-dtrace'
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace --use-adoptium-devkit gcc-11.3.0-Centos7.9.2009-b02'
                ]
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                configureArgs       : [
                        'openj9'    : '--enable-dtrace',
                        'temurin'   : '--enable-dtrace --with-jobs=40'
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace --use-adoptium-devkit gcc-11.3.0-Centos7.6.1810-b02'
                ]
        ],

        aarch64Mac: [
                os                  : 'mac',
                arch                : 'aarch64',
                additionalNodeLabels: 'xcode15.0.1',
                test                : 'default',
                reproducibleCompare : [
                        'temurin'   : true
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        riscv64Linux      :  [
                os                  : 'linux',
                arch                : 'riscv64',
                crossCompile        : 'qemustatic',
                dockerImage         : 'adoptopenjdk/ubuntu2004_build_image:linux-riscv64',
                dockerArgs          : '--platform linux/riscv64',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes --enable-dtrace',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
                ]
        ],

        aarch64Windows: [
                os                  : 'windows',
                arch                : 'aarch64',
                crossCompile        : 'x64',
                additionalNodeLabels: 'win2022&&vs2022',
                test                : 'default',
                buildArgs       : [
                        'temurin'   : '--create-jre-image --create-sbom --cross-compile'
                ]
        ]
  ]

}

Config21 config = new Config21()
return config.buildConfigurations
