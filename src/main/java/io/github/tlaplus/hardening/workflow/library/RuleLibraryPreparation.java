package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.TemporaryDirectory;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Loads the rule module of a metamorphic run once, before decoding (ADR 0017 §1, §6): SANY names
 * its rules, Snowcat types them, and {@link RewriteLibrary} checks the rule contract.
 */
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
            var manifest = manifest(sources, jar, config);
            var names = new LinkedHashSet<>(RuleModuleDefinitions.of(sources, rules.module()));
            var typed = new LibraryTypechecker(jar, scratch, apalache).check(source, Set.of(rules.module()), names);
            return new Prepared(RewriteLibrary.fromModule(typed, names, config.weights()), manifest);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("rule library preparation interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new WorkflowException("cannot prepare rewrite rules: " + exception.getMessage(), exception);
        }
    }

    /** Pins the Apalache distribution, the rule sources, the module and the weights. */
    static String manifest(Path sources, Path jar, MetamorphicConfig config) throws IOException {
        var text = LibraryManifest.snapshot("fuzztla-rules-v1", sources, jar);
        text.append("module ").append(config.rules().orElseThrow().module()).append('\n');
        config.weights().forEach((rule, weight) -> text.append("weight ").append(rule).append(' ').append(weight).append('\n'));
        return text.toString();
    }
}
