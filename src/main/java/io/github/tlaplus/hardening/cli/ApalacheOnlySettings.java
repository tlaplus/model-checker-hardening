package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Settings that {@code fuzztla init} writes only for a corpus that checks with Apalache (ADR 0029
 * §3), found in a corpus that does not. Editing {@code checkers} after {@code init} leaves them
 * behind, and they then quarantine inputs or turn off rules that TLC handles.
 */
final class ApalacheOnlySettings {
    /** The databases that quarantine inputs only for Apalache, by file name. */
    private static final List<Path> DATABASES =
            List.of(KnownDefectDatabase.METAMORPHIC, KnownDefectDatabase.TEMPORAL_APALACHE);

    private ApalacheOnlySettings() {}

    /** Returns one warning per Apalache-only setting of {@code config}, none when it checks with Apalache. */
    static List<String> warnings(FuzzTlaConfig config) {
        if (config.workflow().enabledCheckers().contains(CorpusStage.APALACHE)) {
            return List.of();
        }
        var warnings = new ArrayList<String>();
        for (var database : DATABASES) {
            var name = database.getFileName();
            if (config.workflow().inputs().knownDefects().stream().anyMatch(path -> name.equals(path.getFileName()))) {
                warnings.add("known_defects lists " + name
                        + ", which quarantines inputs only for Apalache, but checkers omit apalache");
            }
        }
        var temporal = config.metamorphic().temporalRules();
        if (temporal.module().isPresent()
                && temporal.weights().entrySet().containsAll(InitialConfig.APALACHE_TEMPORAL_WEIGHTS.entrySet())) {
            warnings.add("temporal_weights turns off " + String.join(" and ", new TreeSet<>(InitialConfig.APALACHE_TEMPORAL_WEIGHTS.keySet()))
                    + ", which only Apalache needs, but checkers omit apalache");
        }
        return List.copyOf(warnings);
    }
}
