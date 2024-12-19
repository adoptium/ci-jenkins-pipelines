class Config8 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac        : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: [
                        temurin : 'xcode11.7',
                        openj9  : 'macos10.14'
                ],
                test                 : 'default',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom'
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
                test: [
                        weekly : ['sanity.openjdk', 'sanity.system', 'extended.system', 'sanity.perf', 'sanity.functional', 'extended.functional', 'extended.openjdk', 'extended.perf', 'special.functional', 'sanity.external', 'dev.openjdk', 'dev.functional']
                ],
                configureArgs       : [
                        'dragonwell'  : '--enable-unlimited-crypto --with-jvm-variants=server  --with-zlib=system',
                        'temurin'     : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-source-archive --create-sbom --enable-sbom-strace'
                ]
        ],

        x64AlpineLinux  : [
                os                  : 'alpine-linux',
                arch                : 'x64',
                dockerImage         : 'adoptopenjdk/alpine3_build_image',
                test                : 'default',
                configureArgs       : [
                        'openj9'    : '--disable-headful',
                        'temurin'   : '--disable-headful --disable-ccache'
                ],
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
                        'openj9'    : '--disable-headful',
                        'temurin'   : '--disable-headful --disable-ccache --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        x64Windows    : [
                os                  : 'windows',
                arch                : 'x64',
                dockerImage         : 'windows2022_build_image',
                dockerRegistry      : 'https://adoptium.azurecr.io',
                dockerCredential    : 'bbb9fa70-a1de-4853-b564-5f02193329ac',
                additionalNodeLabels: 'win2022&&vs2022',
                test                 : 'default',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742'
                ]
        ],

        x32Windows    : [
                os                  : 'windows',
                arch                : 'x86-32',
                dockerImage         : 'windows2022_build_image',
                dockerRegistry      : 'https://adoptium.azurecr.io',
                dockerCredential    : 'bbb9fa70-a1de-4853-b564-5f02193329ac',
                additionalNodeLabels: 'win2022',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs : [
                        temurin : '--jvm-variant client,server --create-sbom --use-adoptium-devkit vs2022_redist_14.40.33807_10.0.26100.1742'
                ],
                test                 : 'default'
        ],

        ppc64Aix      : [
                os  : 'aix',
                arch: 'ppc64',
                additionalNodeLabels: 'xlc13&&aix720',
                test                 : 'default',
                additionalTestLabels : 'sw.os.aix.7_2',
                cleanWorkspaceAfterBuild: true,
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        s390xLinux    : [
                os  : 'linux',
                arch: 's390x',
                test: [
                        temurin: ['sanity.openjdk'],
                        openj9: 'default'
                ],
                dockerImage         : 'rhel7_build_image',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        sparcv9Solaris: [
                os  : 'solaris',
                arch: 'sparcv9',
                test: 'default',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        x64Solaris    : [
                os                  : 'solaris',
                arch                : 'x64',
                test                : 'default',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom'
                ]
        ],

        ppc64leLinux  : [
                os  : 'linux',
                arch: 'ppc64le',
                dockerImage         : 'adoptopenjdk/centos7_build_image',
                test                : 'default',
                configureArgs       : [
                        'temurin'   : '--disable-ccache'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],

        arm32Linux    : [
                os: 'linux',
                arch: 'arm',
                crossCompile: 'aarch64',
                dockerImage: 'adoptopenjdk/ubuntu1604_build_image',
                dockerArgs: '--platform linux/arm/v7',
                test: 'default',
                configureArgs       : [ 
                        'temurin'   : '--disable-ccache --with-jobs=4'
                ],    
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
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
                configureArgs       : [
                        'temurin'   : '--disable-ccache --with-jobs=4'
                ],
                buildArgs           : [
                        'temurin'   : '--create-sbom --enable-sbom-strace'
                ]
        ],
  ]

}

Config8 config = new Config8()
return config.buildConfigurations
