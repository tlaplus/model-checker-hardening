package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel.Decision;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Source;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import java.net.URI;

/**
 * Where the atlas's links point: GitHub source at the atlas's commit, and the atlas's own pages.
 */
final class AtlasLinks {
    private final Source source;

    AtlasLinks(Source source) {
        this.source = source;
    }

    /** The type's declaration on GitHub. */
    String source(TypeNode type) {
        return blob(type.path()) + "#L" + type.line();
    }

    /** The decision record on GitHub. */
    String decision(Decision decision) {
        return blob(decision.path());
    }

    /** The commit on GitHub. */
    String commit() {
        return source.repository() + "/commit/" + source.commit();
    }

    /** The commit, shortened to the usual seven characters when it is a hash. */
    String shortCommit() {
        var commit = source.commit();
        return commit.matches("[0-9a-f]{40}") ? commit.substring(0, 7) : commit;
    }

    /** The explorer, which the repository's GitHub Pages site serves under {@code dev/}. */
    String explorer() {
        var path = URI.create(source.repository()).getPath().split("/");
        return "https://" + path[1] + ".github.io/" + path[2] + "/dev/";
    }

    /** The path of a module page, relative to the atlas root. */
    static String modulePage(String module) {
        return "modules/" + module + ".md";
    }

    /** The path of a type page, relative to the atlas root. */
    static String typePage(TypeNode type) {
        return "types/" + type.name() + ".md";
    }

    private String blob(String path) {
        return source.repository() + "/blob/" + source.commit() + "/" + path;
    }
}
