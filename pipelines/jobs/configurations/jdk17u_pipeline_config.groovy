class Config17 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'xcode15.0.1',
                additionalTestLabels: [
                        openj9      : '!sw.os.osx.10_11'
                ],
                test                : 'default',
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
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
                test: [
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.functional', 'extended.functional', 'extended.openjdk', 'extended.perf', 'special.functional', 'sanity.external', 'dev.openjdk', 'dev.functional']
                ],
                additionalTestLabels: [
                        openj9      : '!(centos6||rhel6)'
                ],
                configureArgs       : [
                        'openj9'    : '--enable-dtrace',
                        'temurin'   : '--enable-dtrace'
                ],
                buildArgs           : [
                        'temurin'   : '--create-source-archive --create-jre-image --create-sbom --enable-sbom-strace'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : '--enable-headless-only=yes',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
                ]
        ],

        aarch64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : [
                        'openj9'    : '--enable-headless-only=yes',
                        'temurin'   : '--enable-headless-only=yes --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
                ]
        ],

        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                dockerImage         : 'win2022_notrhel_image',
                additionalNodeLabels: 'win2022&&vs2022',
                test                : 'default',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742'
                ]
        ],

        x32Windows: [
                os                  : 'windows',
                arch                : 'x86-32',
                dockerImage         : 'win2022_notrhel_image',
                additionalNodeLabels: 'win2022&&vs2022',
                test                : 'default',
                buildArgs           : [
                        'temurin'   : '--jvm-variant client,server --create-jre-image --create-sbom --use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742'
                ]
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64',
                additionalNodeLabels: 'xlc13&&aix720',
                test                : 'default',
                additionalTestLabels: 'sw.os.aix.7_2',
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
                configureArgs       : '--enable-dtrace',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
                ]
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
                ]
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                configureArgs       : [
                        'openj9'    : '--enable-dtrace',
                        'temurin'   : '--enable-dtrace --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
                ]

        ],

        aarch64Mac: [
                os                  : 'mac',
                arch                : 'aarch64',
                additionalNodeLabels: 'xcode15.0.1',
                test                : 'default',
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom'
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
                        'openj9'    : '--enable-dtrace',
                        'temurin'   : '--enable-dtrace --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-jre-image --create-sbom --enable-sbom-strace'
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
                dockerImage         : 'win2022_notrhel_image',
                crossCompile        : 'x64',
                additionalNodeLabels: 'win2022&&vs2022',
                test                : 'default',
                buildArgs       : [
                        'temurin'   : '--create-jre-image --create-sbom --cross-compile --use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742'
                ]
        ]
  ]

}

Config17 config = new Config17()
return config.buildConfigurations
