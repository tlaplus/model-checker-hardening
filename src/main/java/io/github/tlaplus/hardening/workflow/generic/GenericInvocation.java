package io.github.tlaplus.hardening.workflow.generic;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.GenericPlaceholder;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheArguments;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The files and the command line of one generic checker invocation in its working directory
 * (ADR 0033 §3, §4).
 *
 * @param directory the invocation's fresh working directory
 * @param request what the invocation checks
 */
@ArchRole(Role.MODEL)
record GenericInvocation(Path directory, CheckRequest request) {
    GenericInvocation {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(request, "request");
    }

    /** The module as TLA+ source, which {@code {module}} names. */
    Path module() {
        return directory.resolve(FuzzInputModule.MODULE_NAME + ".tla");
    }

    /** The module as Apalache's JSON IR, which {@code {ir}} names. */
    Path ir() {
        return directory.resolve(FuzzInputModule.MODULE_NAME + ".json");
    }

    /** The configuration file, which {@code {config}} names. */
    Path configuration() {
        return directory.resolve(FuzzInputModule.MODULE_NAME + ".cfg");
    }

    /** Returns the command line: the executable and the checker's arguments, expanded. */
    List<String> command(GenericCheckerConfig checker) {
        var values = values();
        var command = new ArrayList<String>();
        command.add(checker.executable().toString());
        checker.arguments().forEach(argument -> command.add(GenericPlaceholder.expand(argument, values)));
        return List.copyOf(command);
    }

    /** Returns the value of every placeholder; one the request lacks, such as a property, is empty. */
    Map<GenericPlaceholder, String> values() {
        var side = FuzzInputModule.entryPoints(request.kind().side());
        var values = new EnumMap<GenericPlaceholder, String>(GenericPlaceholder.class);
        values.put(GenericPlaceholder.DIR, directory.toString());
        values.put(GenericPlaceholder.MODULE, module().toString());
        values.put(GenericPlaceholder.MODULE_NAME, FuzzInputModule.MODULE_NAME);
        values.put(GenericPlaceholder.CONFIG, configuration().toString());
        values.put(GenericPlaceholder.INIT, side.init());
        values.put(GenericPlaceholder.NEXT, side.next());
        values.put(GenericPlaceholder.INV, String.join(",", ApalacheArguments.invariants(request)));
        values.put(GenericPlaceholder.TEMPORAL, ApalacheArguments.temporal(request).orElse(""));
        values.put(GenericPlaceholder.LENGTH, Integer.toString(request.unrollingLength()));
        values.put(GenericPlaceholder.IR, ir().toString());
        return values;
    }
}
