package io.github.tlaplus.hardening.architecture.render;

import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.architecture.model.RoleLabels;
import io.github.tlaplus.hardening.common.ArchModule;
import io.github.tlaplus.hardening.common.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * The interactive explorer: a self-contained page that embeds the model in the template {@value
 * #TEMPLATE} (ADR 0022).
 */
final class ExplorerRenderer {
    /** The explorer's file name, which GitHub Pages serves as the site root. */
    static final String PAGE = "index.html";

    /** The page template, a resource next to this class. */
    static final String TEMPLATE = "explorer.html";
    static final String PLACEHOLDER = "/*ATLAS_DATA*/";

    /** What the page embeds: the model, and the legend and names the page would otherwise repeat. */
    record Payload(ArchModel model, Legend legend, String rootPackage, String leafModule) {}

    /** The roles and relation kinds, in declaration order, with the text the legend shows. */
    record Legend(List<Entry> roles, List<Entry> relations) {
        record Entry(String name, String label, String description) {}

        static Legend current() {
            return new Legend(
                    Arrays.stream(Role.values())
                            .map(r -> new Entry(r.name(), RoleLabels.of(r), r.description()))
                            .toList(),
                    Arrays.stream(RelationKind.values())
                            .map(k -> new Entry(k.name(), k.label(), k.description()))
                            .toList());
        }
    }

    private ExplorerRenderer() {}

    static String render(ArchModel model) throws IOException {
        var template = template();
        var payload = new Payload(model, Legend.current(), ArchModule.ROOT_PACKAGE, MermaidDiagrams.LEAF);
        var json = AtlasJson.MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(payload);
        // Inside a script element, "</" could end the element; "<\/" is the same JSON string.
        return template.replace(PLACEHOLDER, json.replace("</", "<\\/"));
    }

    private static String template() throws IOException {
        try (var in = ExplorerRenderer.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IOException("missing resource " + TEMPLATE);
            }
            var template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var first = template.indexOf(PLACEHOLDER);
            if (first < 0 || first != template.lastIndexOf(PLACEHOLDER)) {
                throw new IOException(TEMPLATE + " must contain " + PLACEHOLDER + " exactly once");
            }
            return template;
        }
    }
}
