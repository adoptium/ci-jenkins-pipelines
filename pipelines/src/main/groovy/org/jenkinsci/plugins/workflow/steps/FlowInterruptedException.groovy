package org.jenkinsci.plugins.workflow.steps

/**
 * Local compatibility stub so pipeline Groovy sources can compile
 * without resolving the Jenkins workflow-step-api artifact from external repositories.
 */
class FlowInterruptedException extends Exception {
    FlowInterruptedException() {
        super()
    }

    FlowInterruptedException(String message) {
        super(message)
    }

    FlowInterruptedException(Throwable cause) {
        super(cause)
    }

    FlowInterruptedException(String message, Throwable cause) {
        super(message, cause)
    }
}
