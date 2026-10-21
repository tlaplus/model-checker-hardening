package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every page of the atlas's views of one model, keyed by its path relative to the atlas root. */
public final class AtlasPages {
    private AtlasPages() {}

    public static Map<String, String> render(ArchModel model) throws IOException {
        var pages = new LinkedHashMap<>(new MarkdownAtlas(model).pages());
        pages.put(ExplorerRenderer.PAGE, ExplorerRenderer.render(model));
        return pages;
    }
}
