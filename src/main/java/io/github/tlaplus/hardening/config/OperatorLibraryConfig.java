package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.ModuleLink;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Source-only library settings. Parsing this record never loads or typechecks a module. */
public record OperatorLibraryConfig(List<Path> classpath, List<Module> modules) {
    public record Module(String module, List<String> operators, ModuleLink link) {
        public Module {
            OperatorId.requireIdentifier(module);
            Objects.requireNonNull(link, "link").requireFor(module);
            operators = List.copyOf(operators);
            if (operators.isEmpty()) {
                throw new IllegalArgumentException("custom module " + module + " has no selected operators");
            }
            operators.forEach(OperatorId::requireIdentifier);
            if (new HashSet<>(operators).size() != operators.size()) {
                throw new IllegalArgumentException("duplicate operator in custom module " + module);
            }
        }

        /** A module under a linkage that names no separate TLC module. */
        public Module(String module, List<String> operators, LibraryLinkage linkage) {
            this(module, operators, ModuleLink.of(module, linkage));
        }

        /** A module whose definitions every checker evaluates inlined. */
        public Module(String module, List<String> operators) {
            this(module, operators, LibraryLinkage.INLINE);
        }

        public LibraryLinkage linkage() {
            return link.linkage();
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

    /**
     * Whether any module's linkage aliases the TLA+ source, which puts the classpath on the TLA+
     * checkers.
     */
    public boolean hasSourceAliases() {
        return modules.stream().anyMatch(module -> module.linkage().aliasesSource());
    }

    /**
     * What goes in front of the class path of the workers that read TLA+ source: the parser and
     * TLC. They resolve instance- and diff-linked modules, and TLC their Java overrides, from it.
     * Apalache reads self-contained JSON and needs none.
     */
    public List<Path> sourceCheckerClasspath() {
        return hasSourceAliases() ? classpath : List.of();
    }

    public OperatorLibraryConfig relativeTo(Path directory) {
        return new OperatorLibraryConfig(ConfigPaths.relativeTo(classpath, directory), modules);
    }

    public static OperatorLibraryConfig empty() {
        return new OperatorLibraryConfig(List.of(), List.of());
    }
}
