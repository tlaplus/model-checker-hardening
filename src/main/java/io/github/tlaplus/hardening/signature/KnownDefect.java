package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Preconditions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One known-defect signature: a documented tool defect and the IR shapes that trigger it.
 *
 * <p>The id is recorded in quarantined corpus entries and in workflow statistics, so it is
 * restricted to lowercase letters, digits, and single hyphens.
 */
public final class KnownDefect {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final String id;
    private final List<String> references;
    private final String description;
    private final List<IrPattern> alternatives;

    KnownDefect(String id, List<String> references, String description, List<IrPattern> alternatives) {
        Objects.requireNonNull(id, "id");
        Preconditions.require(ID.matcher(id).matches(),
                "signature id '" + id + "' must consist of lowercase letters, digits, and hyphens");
        this.id = id;
        this.references = List.copyOf(references);
        Preconditions.require(!this.references.isEmpty(), "signature " + id + " has no references");
        this.references.forEach(reference -> Preconditions.require(
                !reference.isBlank(), "signature " + id + " has a blank reference"));
        this.description = Objects.requireNonNull(description, "description");
        Preconditions.require(!description.isBlank(), "signature " + id + " has no description");
        this.alternatives = List.copyOf(alternatives);
        Preconditions.require(!this.alternatives.isEmpty(), "signature " + id + " has no patterns");
    }

    public String id() {
        return id;
    }

    /** The documents that record the defect, as the database spells them. */
    public List<String> references() {
        return references;
    }

    public String description() {
        return description;
    }

    /** Returns the first of the subexpressions, in their order, that some alternative matches. */
    Optional<TlaEx> firstMatch(List<TlaEx> subexpressions) {
        for (var expression : subexpressions) {
            for (var alternative : alternatives) {
                if (alternative.matches(expression, new Bindings())) {
                    return Optional.of(expression);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }
}
