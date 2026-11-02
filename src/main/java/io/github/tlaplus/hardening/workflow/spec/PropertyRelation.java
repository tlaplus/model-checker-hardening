package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.gen.GeneratedSpec;
import java.util.List;
import java.util.Objects;

/** How the relation module of a metamorphic pair of modules treats their properties and fairness. */
public enum PropertyRelation {
    /**
     * The property and fairness are left out, as before temporal rules (ADR 0016 §3): {@code
     * Fairness} and {@code Prop} are {@code TRUE}.
     */
    OMITTED(FuzzInputModule.RELATION_ENTRY_POINTS),
    /**
     * {@code Fairness} and {@code Prop} are the explored side's, and {@code SpecC} and {@code PropC}
     * the checked side's, so that TLC can check each side's property under its own specification
     * (ADR 0027 §3, §4).
     */
    SEPARATE_RUNS(FuzzInputModule.TEMPORAL_RELATION_ENTRY_POINTS);

    private final List<String> entryPoints;

    PropertyRelation(List<String> entryPoints) {
        this.entryPoints = entryPoints;
    }

    /** Returns every definition a tool evaluates directly in a relation module of this kind. */
    public List<String> entryPoints() {
        return entryPoints;
    }

    /** Returns the relation of a pair whose base is {@code original}: a base without a property omits it. */
    PropertyRelation forBase(GeneratedSpec original) {
        Objects.requireNonNull(original, "original");
        return original.property().isPresent() ? this : OMITTED;
    }
}
