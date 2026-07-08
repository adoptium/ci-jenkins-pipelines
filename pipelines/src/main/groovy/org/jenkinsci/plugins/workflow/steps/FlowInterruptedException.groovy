package org.jenkinsci.plugins.workflow.steps

class FlowInterruptedException extends Exception {
    FlowInterruptedException() {
        super()
    }

    FlowInterruptedException(String message) {
        super(message)
    }

    FlowInterruptedException(String message, Throwable cause) {
        super(message, cause)
    }
}
