package io.github.tlaplus.hardening.workflow;

import tlc2.output.EC;

/** Maps TLC's internal error constants through its public process-exit taxonomy. */
final class TlcOutcomeClassifier {
    private TlcOutcomeClassifier() {}

    static StageOutcome classifyErrorCode(int errorCode) {
        return classifyExitStatus(EC.ExitStatus.errorConstantToExitStatus(errorCode));
    }

    static StageOutcome classifyExitStatus(int exitStatus) {
        if (exitStatus == EC.ExitStatus.SUCCESS) {
            return StageOutcome.PASS;
        }
        return EC.ExitStatus.exitStatusToCrash(exitStatus)
                ? StageOutcome.CRASH
                : StageOutcome.FAIL;
    }
}
