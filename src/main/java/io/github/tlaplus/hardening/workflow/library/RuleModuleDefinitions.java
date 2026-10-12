package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import tla2sany.drivers.FrontEndException;
import tla2sany.drivers.SANY;
import tla2sany.drivers.SanyExitCode;
import tla2sany.drivers.SanySettings;
import tla2sany.modanalyzer.SpecObj;
import tla2sany.output.LogLevel;
import tla2sany.output.SimpleSanyOutput;
import util.SimpleFilenameToStream;

/**
 * Names the top-level definitions of a rule module itself, in declaration order, with the weight
 * each one's comments annotate (ADR 0017 §1). Apalache's typed module flattens the modules it
 * extends and drops comments, so SANY tells rules from helpers and reads the annotations.
 *
 * <p>SANY keeps global state, so a parse runs under one lock. It runs once, while a run prepares
 * its rules and before any stage starts.
 */
final class RuleModuleDefinitions {
    /** The annotation of a rule's default weight, written {@code @weight: n;} as {@code @type:} is. */
    static final String WEIGHT_ANNOTATION = "@weight";

    private static final Pattern ANNOTATION = Pattern.compile(Pattern.quote(WEIGHT_ANNOTATION) + "\\b");
    private static final Pattern WEIGHT = Pattern.compile(Pattern.quote(WEIGHT_ANNOTATION) + ":\\s*(\\d{1,9})\\s*;");

    /** One rule of the module, with the weight its comments annotate, if any. */
    record RuleDefinition(String name, OptionalInt weight) {
        RuleDefinition {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(weight, "weight");
        }
    }

    private RuleModuleDefinitions() {}

    static synchronized List<RuleDefinition> of(Path sources, String module) throws WorkflowException {
        var file = sources.resolve(module + ".tla").toString();
        var diagnostics = new ByteArrayOutputStream();
        try (var stream = new PrintStream(diagnostics, true, StandardCharsets.UTF_8)) {
            var specification = new SpecObj(file, new SimpleFilenameToStream(sources.toString()));
            var exitCode = SANY.parse(specification, file, new SimpleSanyOutput(stream, LogLevel.INFO),
                    SanySettings.defaultSettings());
            if (exitCode != SanyExitCode.OK) {
                throw new WorkflowException("SANY rejects rule module " + module + ":\n"
                        + diagnostics.toString(StandardCharsets.UTF_8));
            }
            var root = specification.getExternalModuleTable().getRootModule();
            var rules = new ArrayList<RuleDefinition>();
            for (var definition : root.getOpDefs()) {
                if (definition.getOriginallyDefinedInModuleNode() == root) {
                    var name = definition.getName().toString();
                    rules.add(new RuleDefinition(name, weight(name, definition.getPreComments())));
                }
            }
            return List.copyOf(rules);
        } catch (FrontEndException exception) {
            throw new WorkflowException("SANY cannot parse rule module " + module + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Reads the weight annotation of one rule from the comments before it.
     *
     * @throws WorkflowException if an annotation is malformed or the rule has two
     */
    static OptionalInt weight(String rule, String[] comments) throws WorkflowException {
        var weight = OptionalInt.empty();
        for (var comment : comments) {
            var annotation = ANNOTATION.matcher(comment);
            while (annotation.find()) {
                var value = WEIGHT.matcher(comment).region(annotation.start(), comment.length());
                if (!value.lookingAt()) {
                    throw new WorkflowException("rewrite rule " + rule + ": malformed annotation, expected "
                            + WEIGHT_ANNOTATION + ": n;");
                }
                if (weight.isPresent()) {
                    throw new WorkflowException("rewrite rule " + rule + ": more than one " + WEIGHT_ANNOTATION);
                }
                weight = OptionalInt.of(Integer.parseInt(value.group(1)));
            }
        }
        return weight;
    }
}
