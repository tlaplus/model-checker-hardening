package io.github.tlaplus.hardening.workflow;

import java.util.Objects;

/** One SANY request result returned by an isolated parser worker. */
record ParserResult(Outcome outcome, String diagnostic) {
    enum Outcome {
        PASS(0, "pass"),
        FAIL(1, "fail"),
        CRASH(2, "crashed");

        private final int protocolCode;
        private final String corpusVerdict;

        Outcome(int protocolCode, String corpusVerdict) {
            this.protocolCode = protocolCode;
            this.corpusVerdict = corpusVerdict;
        }

        int protocolCode() {
            return protocolCode;
        }

        String corpusVerdict() {
            return corpusVerdict;
        }

        static Outcome fromProtocolCode(int code) throws WorkflowException {
            for (var outcome : values()) {
                if (outcome.protocolCode == code) {
                    return outcome;
                }
            }
            throw new WorkflowException("parser worker returned unknown verdict code: " + code);
        }
    }

    ParserResult {
        Objects.requireNonNull(outcome, "outcome");
        diagnostic = Objects.requireNonNullElse(diagnostic, "");
    }
}
