package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.config.ToolsConfig;
import io.github.tlaplus.hardening.workflow.CorpusTools;
import io.github.tlaplus.hardening.workflow.StagedTools;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Copies into a new corpus every file its configuration names (ADR 0025 §2, §3), so the corpus
 * keeps the releases it was initialized with:
 *
 * <ul>
 *   <li>the classpath of the operator library, of the rewrite rules and of the edge guards goes to
 *       {@code tla/}. A file entry is copied with a sibling {@code VERSION} file that records its
 *       provenance; a directory entry as its top-level regular files. An entry two of them share is
 *       copied once;
 *   <li>the tool JARs go to {@code tools/}: those the configuration names, else the staged ones.
 * </ul>
 */
@ArchRole(Role.STORE)
@Key
public final class LibraryInstall {
    /** The corpus subdirectory, relative to {@code config.toml}, that holds library sources. */
    public static final Path DIRECTORY = Path.of("tla");

    /** The provenance file copied beside a file entry. */
    private static final String VERSION = "VERSION";

    /**
     * What {@link #copy} installs.
     *
     * @param config the configuration naming the installed copies, relative to the corpus
     * @param classpath each classpath source, absolute, and its copy, relative to the corpus
     * @param tools each tool JAR, absolute, and its copy, relative to the corpus
     */
    public record Plan(FuzzTlaConfig config, Map<Path, Path> classpath, Map<Path, Path> tools) {
        public Plan {
            Objects.requireNonNull(config, "config");
            classpath = Collections.unmodifiableMap(new LinkedHashMap<>(classpath));
            tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        }
    }

    private LibraryInstall() {}

    /**
     * Plans the installation of {@code config}, whose paths are absolute, as {@code TomlConfig.read}
     * returns them.
     */
    public static Plan plan(FuzzTlaConfig config) throws WorkflowException {
        var classpath = new Classpath();
        var libraries = config.libraries();
        var metamorphic = config.metamorphic();
        var installed = config
                .withLibraries(new OperatorLibraryConfig(classpath.install(libraries.classpath()), libraries.modules()))
                .withMetamorphic(new MetamorphicConfig(
                        classpath.install(metamorphic.rules()), metamorphic.limits(), metamorphic.adoption()))
                .withGraphGuards(classpath.install(config.graphGuards()))
                .withTools(ToolsConfig.installed());
        var tools = new LinkedHashMap<Path, Path>();
        for (var tool : ToolJar.values()) {
            var source = config.tools().jar(tool);
            tools.put(source.isPresent() ? source.get() : StagedTools.locate(tool),
                    ToolsConfig.DIRECTORY.resolve(tool.fileName()));
        }
        CorpusTools.require(config);
        return new Plan(installed, classpath.copies, tools);
    }

    /** Copies every planned source under {@code corpusRoot}, never replacing a file. */
    public static void copy(Plan plan, Path corpusRoot) throws IOException {
        var versions = new LinkedHashSet<Path>();
        for (var entry : plan.classpath().entrySet()) {
            var source = entry.getKey();
            var target = corpusRoot.resolve(entry.getValue());
            Files.createDirectories(target.getParent());
            if (Files.isDirectory(source)) {
                var directory = Files.createDirectory(target);
                for (var file : regularFiles(source)) Files.copy(file, directory.resolve(file.getFileName()));
            } else {
                Files.copy(source, target);
                var version = source.resolveSibling(VERSION);
                if (Files.isRegularFile(version)) versions.add(version);
            }
        }
        for (var version : versions) Files.copy(version, corpusRoot.resolve(DIRECTORY).resolve(VERSION));
        for (var entry : plan.tools().entrySet()) {
            var target = corpusRoot.resolve(entry.getValue());
            Files.createDirectories(target.getParent());
            Files.copy(entry.getKey(), target);
        }
    }

    /** The classpath entries planned so far, each named once under {@link #DIRECTORY}. */
    private static final class Classpath {
        private final Map<Path, Path> copies = new LinkedHashMap<>();
        private final Set<Path> names = new HashSet<>();

        List<Path> install(List<Path> entries) throws WorkflowException {
            var result = new ArrayList<Path>(entries.size());
            for (var entry : entries) {
                var installed = copies.get(entry);
                if (installed == null) {
                    if (!Files.exists(entry)) throw new WorkflowException("library classpath entry does not exist: " + entry);
                    installed = DIRECTORY.resolve(entry.getFileName());
                    if (!names.add(installed)) {
                        throw new WorkflowException("library classpath entries share the name " + installed);
                    }
                    copies.put(entry, installed);
                }
                result.add(installed);
            }
            return result;
        }

        RuleLibraryConfig install(RuleLibraryConfig rules) throws WorkflowException {
            if (rules.module().isEmpty()) return rules;
            var module = rules.module().get();
            return new RuleLibraryConfig(
                    Optional.of(new RuleLibraryConfig.RuleModule(module.module(), install(module.classpath()))),
                    rules.weights());
        }
    }

    private static List<Path> regularFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
