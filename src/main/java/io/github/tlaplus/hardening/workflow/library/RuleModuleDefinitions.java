package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tla2sany.drivers.FrontEndException;
import tla2sany.drivers.SANY;
import tla2sany.drivers.SanyExitCode;
import tla2sany.drivers.SanySettings;
import tla2sany.modanalyzer.SpecObj;
import tla2sany.output.LogLevel;
import tla2sany.output.SimpleSanyOutput;
import util.SimpleFilenameToStream;

/**
 * Names the top-level definitions of a rule module itself, in declaration order (ADR 0017 §1).
 * Apalache's typed module flattens the modules it extends, so SANY tells rules from helpers.
 *
 * <p>SANY keeps global state, so a parse runs under one lock. It runs once, while a run prepares
 * its rules and before any stage starts.
 */
final class RuleModuleDefinitions {
    private RuleModuleDefinitions() {}

    static synchronized List<String> of(Path sources, String module) throws WorkflowException {
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
            var names = new ArrayList<String>();
            for (var definition : root.getOpDefs()) {
                if (definition.getOriginallyDefinedInModuleNode() == root) {
                    names.add(definition.getName().toString());
                }
            }
            return List.copyOf(names);
        } catch (FrontEndException exception) {
            throw new WorkflowException("SANY cannot parse rule module " + module + ": " + exception.getMessage(), exception);
        }
    }
}
