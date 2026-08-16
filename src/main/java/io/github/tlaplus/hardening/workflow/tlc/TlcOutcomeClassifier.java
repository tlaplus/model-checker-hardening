package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import tlc2.output.EC;

/** Maps TLC's internal error constants to workflow outcomes. */
final class TlcOutcomeClassifier {
    private TlcOutcomeClassifier() {}

    static StageOutcome classifyErrorCode(int errorCode) {
        if (errorCode == EC.TLC_INTEGER_TOO_BIG) {
            return StageOutcome.FAIL;
        }
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
