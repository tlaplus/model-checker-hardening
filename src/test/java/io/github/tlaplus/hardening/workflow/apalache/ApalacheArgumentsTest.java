package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApalacheArgumentsTest {
    private static final Path JOB = Path.of("job");
    private static final Path SPECIFICATION = Path.of("job", "FuzzInput.json");

    @Test
    void checksTheInvariantOverTheStepBound() {
        assertEquals(
                List.of("--out-dir=" + JOB.resolve("out"), "check", "--init=Init", "--next=Next", "--inv=Inv",
                        "--length=5", "--no-deadlock", SPECIFICATION.toString()),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, CheckRequest.invariant(5))));
    }

    @Test
    void checksTheLivenessImplicationOneTransitionPastTheStepBound() {
        assertEquals(
                List.of("--out-dir=" + JOB.resolve("out"), "check", "--init=Init", "--next=Next", "--inv=Inv",
                        "--temporal=Liveness", "--length=6", "--no-deadlock", SPECIFICATION.toString()),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, new CheckRequest(5, CheckKind.INVARIANT_AND_PROPERTY))));
    }

    @Test
    void checksTheActionInvariantBesideTheInvariant() {
        assertEquals(
                List.of("--out-dir=" + JOB.resolve("out"), "check", "--init=Init", "--next=Next",
                        "--inv=Inv,Step", "--length=5", "--no-deadlock", SPECIFICATION.toString()),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, new CheckRequest(5, CheckKind.RELATION))));
    }

    /** The property runs follow in the same worker (ADR 0029 §1), so the relation is asked as before. */
    @Test
    void checksTheRelationBeforeThePropertyRunsAsTheRelation() {
        assertEquals(
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, new CheckRequest(5, CheckKind.RELATION))),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION,
                        new CheckRequest(5, CheckKind.RELATION_AND_PROPERTIES))));
    }

    @Test
    void checksTheExploredPropertyDirectlyFromInitAndNext() {
        assertEquals(
                List.of("--out-dir=" + JOB.resolve("out"), "check", "--init=Init", "--next=Next",
                        "--temporal=Prop", "--length=6", "--no-deadlock", SPECIFICATION.toString()),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, new CheckRequest(5, CheckKind.EXPLORED_PROPERTY))));
    }

    @Test
    void checksTheCheckedPropertyFromTheCheckedSide() {
        assertEquals(
                List.of("--out-dir=" + JOB.resolve("out"), "check", "--init=InitC", "--next=NextC",
                        "--temporal=PropC", "--length=6", "--no-deadlock", SPECIFICATION.toString()),
                List.of(ApalacheArguments.check(JOB, SPECIFICATION, new CheckRequest(5, CheckKind.CHECKED_PROPERTY))));
    }
}
