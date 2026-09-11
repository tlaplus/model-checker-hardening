package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * The known-defect signatures of one invocation, in database order.
 *
 * <p>Several database files concatenate in the order they are listed. Signature ids are unique
 * across all of them, because an id names a signature in corpus entries and statistics. Matching
 * is a deterministic function of the module and the database.
 */
public final class KnownDefectDatabase {
    /** The database the repository ships, relative to the project directory. */
    public static final Path SHIPPED = Path.of("signatures", "known-defects.toml");

    private static final KnownDefectDatabase EMPTY = new KnownDefectDatabase(List.of());

    private final List<KnownDefect> signatures;

    private KnownDefectDatabase(List<KnownDefect> signatures) {
        this.signatures = List.copyOf(signatures);
    }

    /** The database of a run that configures none: it matches nothing. */
    public static KnownDefectDatabase empty() {
        return EMPTY;
    }

    /** Reads and concatenates database files, rejecting an id that two signatures share. */
    public static KnownDefectDatabase load(List<Path> files) throws KnownDefectDatabaseException {
        Objects.requireNonNull(files, "files");
        var signatures = new ArrayList<KnownDefect>();
        var origins = new HashMap<String, Path>();
        for (var file : files) {
            for (var signature : KnownDefectDatabaseReader.read(file)) {
                var previous = origins.putIfAbsent(signature.id(), file);
                if (previous != null) {
                    throw new KnownDefectDatabaseException(
                            file + ": signature '" + signature.id() + "' is already defined in "
                                    + previous);
                }
                signatures.add(signature);
            }
        }
        return signatures.isEmpty() ? EMPTY : new KnownDefectDatabase(signatures);
    }

    public boolean isEmpty() {
        return signatures.isEmpty();
    }

    public List<KnownDefect> signatures() {
        return signatures;
    }

    /**
     * Returns every signature that matches what the tools evaluate in the module, in database
     * order: the root definitions and every definition they reference, transitively.
     *
     * @throws IllegalArgumentException if the module does not define a root
     */
    public List<KnownDefectMatch> matches(TlaModule module, List<String> roots) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(roots, "roots");
        if (signatures.isEmpty()) {
            return List.of();
        }
        var subexpressions = IrTree.evaluatedSubexpressions(module, roots);
        var result = new ArrayList<KnownDefectMatch>();
        for (var signature : signatures) {
            signature.firstMatch(subexpressions)
                    .ifPresent(witness -> result.add(new KnownDefectMatch(signature, witness)));
        }
        return List.copyOf(result);
    }
}
