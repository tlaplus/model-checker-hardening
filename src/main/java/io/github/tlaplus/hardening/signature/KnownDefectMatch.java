package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import java.util.Objects;

/** A signature that matches a module, and the outermost subexpression it matched first. */
@ArchRole(Role.MODEL)
@Key
public record KnownDefectMatch(KnownDefect defect, TlaEx witness) {
    public KnownDefectMatch {
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(witness, "witness");
    }
}
