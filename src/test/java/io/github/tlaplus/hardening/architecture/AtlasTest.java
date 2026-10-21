package io.github.tlaplus.hardening.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.extract.AtlasSettings;
import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeKind;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.common.ArchModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Builds the atlas of the main sources once and checks what it extracted and wrote. */
class AtlasTest {
    private static final String ROOT = ArchModule.ROOT_PACKAGE;

    @TempDir
    static Path output;

    private static ArchModel model;
    private static Map<String, TypeNode> types;

    @BeforeAll
    static void build() throws IOException {
        var settings = AtlasSettings.defaults(Path.of("").toAbsolutePath()).withOutput(output.resolve("atlas"));
        AtlasMain.run(settings);
        model = AtlasJson.read(settings.output().resolve(AtlasJson.MODEL_FILE));
        types = model.types().stream().collect(Collectors.toMap(TypeNode::name, Function.identity()));
    }

    @Test
    void coversEveryTopLevelTypeAndPackage() throws IOException {
        var sources = Path.of("src/main/java");
        try (var files = Files.walk(sources)) {
            // A file may declare further package-private top-level types besides its own.
            var named = files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("package-info.java"))
                    .map(f -> sources.relativize(f).toString().replace(File.separatorChar, '.'))
                    .map(n -> n.substring(0, n.length() - ".java".length()))
                    .toList();
            assertTrue(types.keySet().containsAll(named));
            assertTrue(model.types().size() >= named.size());
        }
        assertEquals(ArchModule.values().length, model.modules().size());
        assertTrue(model.packages().stream().map(PackageNode::name).anyMatch((ROOT + ".workflow.tlc")::equals));
    }

    @Test
    void readsKindSummaryAndPosition() {
        var stage = types.get(ROOT + ".corpus.CorpusStage");
        assertEquals(TypeKind.ENUM, stage.kind());
        assertEquals("One implemented stage of the fuzzing pipeline, and the corpus directories it owns.",
                stage.summary());
        assertEquals("src/main/java/io/github/tlaplus/hardening/corpus/CorpusStage.java", stage.path());
        assertTrue(stage.line() > 1);
        assertEquals("corpus", model.packages().stream()
                .filter(p -> p.name().equals(ROOT + ".corpus")).findFirst().orElseThrow().module());
    }

    @Test
    void rendersInlineTagsAndCollectsDecisions() {
        assertEquals("A mutable per-verdict count, built up while scanning a corpus and read as "
                + "`StageEntryCounts`.", types.get(ROOT + ".corpus.VerdictTally").summary());
        assertTrue(types.get(ROOT + ".config.ConfigSchema").summary().startsWith(
                "The declaration of every key in `config.toml`:"));
        var database = model.packages().stream()
                .filter(p -> p.name().equals(ROOT + ".database")).findFirst().orElseThrow();
        assertTrue(database.adrs().contains("0009"), database::toString);
        assertTrue(model.decisions().stream().anyMatch(d -> d.number().equals("0009")
                && d.title().equals("Corpus database")
                && d.path().equals("docs/decisions/0009-corpus-database.md")));
    }

    @Test
    void classifiesRelations() {
        assertEquals(RelationKind.HAS, kind("checker.ExplorationMetrics", "checker.ExplorationPhase"));
        assertEquals(RelationKind.DEPENDS, kind("Main", "cli.FuzzTlaCommand"));
        assertTrue(model.relations().stream().noneMatch(r -> r.from().equals(r.to())));
        assertTrue(model.relations().stream().map(Relation::kind).distinct().count() >= 4);
    }

    @Test
    void foldsModuleEdgesThatTheLayeringAllows() {
        assertTrue(model.moduleEdges().stream().anyMatch(e -> e.from().equals("root") && e.to().equals("cli")));
        assertTrue(model.moduleEdges().stream().allMatch(e -> e.allowed()), model.moduleEdges()::toString);
    }

    @Test
    void writesMarkdownPages() throws IOException {
        var atlas = output.resolve("atlas");
        assertTrue(Files.readString(atlas.resolve("README.md")).contains("```mermaid\nflowchart TB\n"));
        assertTrue(Files.readString(atlas.resolve("index.html")).contains("\"rootPackage\":"));
        assertTrue(Files.readString(atlas.resolve("modules/corpus.md")).contains("CorpusStage"));
        assertTrue(Files.readString(atlas.resolve("types/" + ROOT + ".corpus.CorpusStage.md"))
                .contains("[source](https://github.com/tlaplus/model-checker-hardening/blob/main/"));
    }

    private static RelationKind kind(String from, String to) {
        return model.relations().stream()
                .filter(r -> r.from().equals(ROOT + "." + from) && r.to().equals(ROOT + "." + to))
                .map(Relation::kind).findFirst().orElseThrow(() -> new AssertionError(from + " -> " + to));
    }
}
