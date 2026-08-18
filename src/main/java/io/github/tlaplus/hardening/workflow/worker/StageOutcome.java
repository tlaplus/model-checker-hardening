package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.workflow.WorkflowException;

/** Common result of processing one input in a checking stage. */
public enum StageOutcome {
    PASS(0, CorpusVerdict.PASS),
    FAIL(1, CorpusVerdict.FAIL),
    CRASH(2, CorpusVerdict.CRASH);

    private final int protocolCode;
    private final CorpusVerdict corpusVerdict;

    StageOutcome(int protocolCode, CorpusVerdict corpusVerdict) {
        this.protocolCode = protocolCode;
        this.corpusVerdict = corpusVerdict;
    }

    int protocolCode() {
        return protocolCode;
    }

    /** Returns the corpus verdict directory this outcome sends an input to. */
    public CorpusVerdict corpusVerdict() {
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
