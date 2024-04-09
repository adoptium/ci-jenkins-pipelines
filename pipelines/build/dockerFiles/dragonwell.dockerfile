ARG image

FROM $image

RUN \
    # Dragonewell 8 requires a dragonwell 8 BootJDK
    mkdir -p /opt/dragonwell; \
    wget https://github.com/alibaba/dragonwell8/releases/download/dragonwell-8.4.4_jdk8u262-ga/Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz; \
    test $(sha256sum Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz | cut -d ' ' -f1) = "d54eaeb5362dfe8a94b3c9b464c99ca04c1a80aa1ad39539b44e08e4858671f6" || exit 1; \
    tar -xf Alibaba_Dragonwell_8.4.4-GA_Linux_x64.tar.gz -C /opt/; \
    mv /opt/jdk8u262-b10 /opt/dragonwell8

ENV JDK7_BOOT_DIR="/opt/dragonwell8"
