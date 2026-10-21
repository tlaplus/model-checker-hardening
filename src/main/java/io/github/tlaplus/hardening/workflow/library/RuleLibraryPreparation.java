package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.common.TemporaryDirectory;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Loads the rule module of a metamorphic run once, before decoding (ADR 0017 §1, §6): SANY names
 * its rules and reads their weight annotations, Snowcat types them, and {@link RewriteLibrary}
 * checks the rule contract.
 */
@ArchRole(Role.FACADE)
@Key
public final class RuleLibraryPreparation {
    /**
     * The checked rules, beside the replay identity of the sources they were read from. The
     * manifest is empty when no rule module is configured.
     */
    public record Prepared(RewriteLibrary library, String manifest) {}

    private RuleLibraryPreparation() {}

    /** Typechecks with the Apalache settings of the run, as custom operator libraries are. */
    public static Prepared prepare(MetamorphicConfig config, CheckerStageConfig apalache) throws WorkflowException {
        if (config.rules().isEmpty()) {
            return new Prepared(RewriteLibrary.empty(), "");
        }
        var rules = config.rules().get();
        try (var temporary = TemporaryDirectory.create("fuzztla-rules-")) {
            var scratch = temporary.path();
            var sources = Files.createDirectory(scratch.resolve("sources"));
            var jar = ApalacheDistribution.locate();
            LibrarySources.snapshot(rules.classpath(), sources, jar);
            var source = sources.resolve(rules.module() + ".tla");
            if (!Files.isRegularFile(source)) {
                throw new WorkflowException("rule module not found on metamorphic.rules classpath: " + rules.module());
            }
            var definitions = RuleModuleDefinitions.of(sources, rules.module());
            var names = new LinkedHashSet<String>();
            definitions.forEach(definition -> names.add(definition.name()));
            var weights = weights(definitions, config.weights());
            var manifest = manifest(sources, jar, rules.module(), weights);
            var typed = new LibraryTypechecker(jar, scratch, apalache).check(source, Set.of(rules.module()), names);
            return new Prepared(RewriteLibrary.fromModule(typed, names, weights), manifest);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("rule library preparation interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new WorkflowException("cannot prepare rewrite rules: " + exception.getMessage(), exception);
        }
    }

    /**
     * Returns the weight of every rule that its annotation or the configuration weighs, in
     * declaration order; the configured weight overrides the annotation. A configured name that is
     * no rule stays in the map, so {@link RewriteLibrary#fromModule} reports it.
     */
    static Map<String, Integer> weights(
            List<RuleModuleDefinitions.RuleDefinition> definitions, Map<String, Integer> configured) {
        var weights = new LinkedHashMap<String, Integer>();
        for (var definition : definitions) {
            definition.weight().ifPresent(weight -> weights.put(definition.name(), weight));
            if (configured.containsKey(definition.name())) {
                weights.put(definition.name(), configured.get(definition.name()));
            }
        }
        new TreeMap<>(configured).forEach(weights::putIfAbsent);
        return weights;
    }

    /** Pins the Apalache distribution, the rule sources, the module and the effective weights. */
    static String manifest(Path sources, Path jar, String module, Map<String, Integer> weights) throws IOException {
        var text = LibraryManifest.snapshot("fuzztla-rules-v1", sources, jar);
        text.append("module ").append(module).append('\n');
        weights.forEach((rule, weight) -> text.append("weight ").append(rule).append(' ').append(weight).append('\n'));
        return text.toString();
    }
}
