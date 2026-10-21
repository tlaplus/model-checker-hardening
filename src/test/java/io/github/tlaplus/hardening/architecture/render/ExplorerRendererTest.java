package io.github.tlaplus.hardening.architecture.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Source;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.common.ArchModule;
import io.github.tlaplus.hardening.common.Role;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplorerRendererTest {
    @Test
    void embedsTheModelAndTheLegend() throws IOException {
        // A summary with "</script>" must not end the element that embeds it.
        var type = RenderFixtures.store(ArchModule.ROOT_PACKAGE + ".corpus.CorpusLock");
        var module = new ModuleNode("corpus", ArchModule.ROOT_PACKAGE + ".corpus", "ends </script> here", List.of());
        var tricky = new ArchModel(new Source("https://github.com/a/b", "main"), List.of(), List.of(module),
                List.of(), List.of(type), List.of(), List.of());

        var page = ExplorerRenderer.render(tricky);
        assertFalse(page.contains(ExplorerRenderer.PLACEHOLDER));
        var open = "<script id=\"atlas-data\" type=\"application/json\">";
        var start = page.indexOf(open) + open.length();
        var data = page.substring(start, page.indexOf("</script>", start));
        var payload = AtlasJson.MAPPER.readValue(data, ExplorerRenderer.Payload.class);
        assertEquals(tricky, payload.model());
        assertEquals(Role.values().length, payload.legend().roles().size());
        assertEquals(RelationKind.values().length, payload.legend().relations().size());
        assertEquals(ArchModule.ROOT_PACKAGE, payload.rootPackage());
    }
}
