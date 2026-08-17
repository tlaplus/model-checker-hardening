package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.workflow.WorkflowException;

/** Common result of processing one input in a checking stage. */
public enum StageOutcome {
    PASS(0, "pass"),
    FAIL(1, "fail"),
    CRASH(2, "crashed");

    private final int protocolCode;
    private final String corpusVerdict;

    StageOutcome(int protocolCode, String corpusVerdict) {
        this.protocolCode = protocolCode;
        this.corpusVerdict = corpusVerdict;
    }

    int protocolCode() {
        return protocolCode;
    }

    public String corpusVerdict() {
        return corpusVerdict;
    }

    static StageOutcome fromProtocolCode(int code) throws WorkflowException {
        for (var outcome : values()) {
            if (outcome.protocolCode == code) {
                return outcome;
            }
        }
        throw new WorkflowException("worker returned unknown verdict code: " + code);
    }
}
