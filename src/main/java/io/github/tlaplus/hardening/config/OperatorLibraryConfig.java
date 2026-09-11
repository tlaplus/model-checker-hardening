package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Source-only library settings. Parsing this record never loads or typechecks a module. */
public record OperatorLibraryConfig(List<Path> classpath, List<Module> modules) {
    public record Module(String module, List<String> operators) {
        public Module {
            OperatorId.requireIdentifier(module);
            operators = List.copyOf(operators);
            if (operators.isEmpty()) {
                throw new IllegalArgumentException("custom module " + module + " has no selected operators");
            }
            operators.forEach(OperatorId::requireIdentifier);
            if (new HashSet<>(operators).size() != operators.size()) {
                throw new IllegalArgumentException("duplicate operator in custom module " + module);
            }
        }
    }

    public OperatorLibraryConfig {
        classpath = List.copyOf(classpath);
        modules = List.copyOf(modules);
        var names = new HashSet<String>();
        for (var module : modules) {
            if (!names.add(module.module())) {
                throw new IllegalArgumentException("duplicate custom module " + module.module());
            }
        }
    }

    public List<OperatorId> operators() {
        var result = new ArrayList<OperatorId>();
        for (var module : modules) {
            module.operators().forEach(name -> result.add(new OperatorId(module.module(), name)));
        }
        return List.copyOf(result);
    }

    public OperatorLibraryConfig relativeTo(Path directory) {
        return new OperatorLibraryConfig(ConfigPaths.relativeTo(classpath, directory), modules);
    }

    public static OperatorLibraryConfig empty() {
        return new OperatorLibraryConfig(List.of(), List.of());
    }
}
