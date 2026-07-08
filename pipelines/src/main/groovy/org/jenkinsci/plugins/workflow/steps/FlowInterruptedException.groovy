package org.jenkinsci.plugins.workflow.steps

class FlowInterruptedException extends Exception {
    // Local compile-time stub for the Jenkins workflow exception used in catch clauses.
    private static final long serialVersionUID = 1L

    FlowInterruptedException() {
        super()
    }

    FlowInterruptedException(String message) {
        super(message)
    }

    FlowInterruptedException(String message, Throwable cause) {
        super(message, cause)
    }

    FlowInterruptedException(Throwable cause) {
        super(cause)
    }
}
