package io.github.tlaplus.hardening.workflow;

/** Common result of processing one input in a checking stage. */
enum StageOutcome {
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

    String corpusVerdict() {
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
