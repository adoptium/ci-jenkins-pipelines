package org.jenkinsci.plugins.workflow.steps;

/**
 * Compile-time-only stub for Groovy test builds when Jenkins workflow artifacts are unavailable.
 * It is added only to the local Gradle compilation/test classpaths and must not be relied on for
 * Jenkins runtime behavior.
 */
public class FlowInterruptedException extends Exception {
    public FlowInterruptedException() {
        super();
    }

    public FlowInterruptedException(String message) {
        super(message);
    }

    public FlowInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowInterruptedException(Throwable cause) {
        super(cause);
    }
}
