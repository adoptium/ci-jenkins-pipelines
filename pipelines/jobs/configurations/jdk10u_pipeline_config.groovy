class Config10 {

    final Map<String, Map<String, ?>> buildConfigurations = [
        x64Mac    : [
                os                  : 'mac',
                arch                : 'x64',
                additionalNodeLabels: 'build-macstadium-macos1010-1',
                configureArgs       : '--enable-dtrace=auto'
        ],
        x64Linux  : [
                os                  : 'linux',
                arch                : 'x64',
                additionalNodeLabels: 'centos6',
                configureArgs       : '--enable-dtrace=auto'
        ],

        // Currently we have to be quite specific about which windows to use as not all of them have freetype installed
        x64Windows: [
                os                  : 'windows',
                arch                : 'x64',
                additionalNodeLabels: [
                        hotspot: 'win2012',
                        openj9:  'win2012&&mingw-cygwin'
                ]
        ],

        ppc64Aix    : [
                os                  : 'aix',
                arch                : 'ppc64'
        ],

        s390xLinux    : [
                os                  : 'linux',
                arch                : 's390x',
                additionalNodeLabels: [
                        hotspot: 'rhel7'
                ],
                configureArgs       : '--enable-dtrace=auto'
        ],

        sparcv9Solaris    : [
                os                  : 'solaris',
                arch                : 'sparcv9',
                configureArgs       : '--enable-dtrace=auto'
        ],

        ppc64leLinux    : [
                os                  : 'linux',
                arch                : 'ppc64le',
                configureArgs       : '--enable-dtrace=auto'
        ],

        arm32Linux    : [
                os                  : 'linux',
                arch                : 'arm',
                configureArgs       : '--enable-dtrace=auto'
        ],

        aarch64Linux    : [
                os                  : 'linux',
                arch                : 'aarch64',
                additionalNodeLabels: 'centos7',
                configureArgs       : '--enable-dtrace=auto'
        ],

        /*
        "x86-32Windows"    : [
                os                 : 'windows',
                arch               : 'x86-32',
                additionalNodeLabels: 'win2012&&x86-32'
        ],
        */
        x64LinuxXL    : [
                os                   : 'linux',
                additionalNodeLabels : 'centos6',
                arch                 : 'x64',
                additionalFileNameTag: 'linuxXL',
                configureArgs        : '--with-noncompressedrefs --enable-dtrace=auto'
        ]
  ]

}

Config10 config = new Config10()
return config.buildConfigurations
