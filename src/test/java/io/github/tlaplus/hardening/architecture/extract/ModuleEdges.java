package io.github.tlaplus.hardening.architecture.extract;

import io.github.tlaplus.hardening.architecture.extract.DependencyScanner.ClassEdge;
import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleEdge;
import io.github.tlaplus.hardening.architecture.model.TransitiveReduction.Arc;
import io.github.tlaplus.hardening.architecture.model.TransitiveReduction;
import io.github.tlaplus.hardening.common.ArchModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Class edges folded into the module edges of the atlas model and of {@code LayeringTest}. */
public final class ModuleEdges {
    private ModuleEdges() {}

    /**
     * One edge per pair of distinct modules that a class edge connects, in module declaration
     * order: how many class edges it folds, whether {@link ArchModule} allows it, whether a path
     * through other modules implies it, and its first class edge.
     */
    public static List<ModuleEdge> of(List<ClassEdge> edges) {
        var byArc = new TreeMap<Arc<ArchModule>, List<ClassEdge>>(
                Comparator.comparing((Arc<ArchModule> a) -> a.from()).thenComparing(Arc::to));
        for (var edge : edges) {
            var from = DependencyScanner.moduleOf(edge.from());
            var to = DependencyScanner.moduleOf(edge.to());
            if (from != to) {
                byArc.computeIfAbsent(new Arc<>(from, to), a -> new ArrayList<>()).add(edge);
            }
        }
        var implied = TransitiveReduction.implied(byArc.keySet());
        return byArc.entrySet().stream()
                .map(e -> {
                    var arc = e.getKey();
                    var first = e.getValue().getFirst();
                    return new ModuleEdge(
                            arc.from().displayName(),
                            arc.to().displayName(),
                            e.getValue().size(),
                            arc.from().allows(arc.to()),
                            implied.contains(arc),
                            first.from(),
                            first.to());
                })
                .toList();
    }
}
