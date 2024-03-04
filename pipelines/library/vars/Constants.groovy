class Constants {
    static final String USERNAME = "jenkins";
    static final String[] IGNORE_LABELS = ['.ignore'];
    static final String REMOTE_FS = "/home/${USERNAME}";
    static final String WIN_REMOTE_FS = "C:\\Users\\${USERNAME}";
    static final String OSX_REMOTE_FS = "/Users/${USERNAME}";
    
    // This the key that'll be used for SSHLauncher in CreateNewNode
    static final String SSH_CREDENTIAL_ID = "";
    
    static final String SSH_COMMAND = "ssh -C -i ${SSH_KEY_LOCATION} ${USERNAME}@";
    static final String SSH_KEY_LOCATION = "";

    static final String OS_LABEL_FORMAT = "%s.%s.%s";
    static final String GENERIC_LABEL_FORMAT = "%s.%s";
    static final String OS_LABEL_PREFIX = "sw.os";
    static final String ENDIAN_LABEL_PREFIX = "hw.endian";
    static final String PLATFORM_LABEL_PREFIX = "hw.platform";
    static final String ARCH_LABEL_PREFIX = "hw.arch";
    static final String KERNEL_LABEL_PREFIX = "hw.kernel";
    static final String HYPERVISOR_LABEL_PREFIX = "hw.hypervisor";
}
