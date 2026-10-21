package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel;
import java.util.Map;

/** Every page of the atlas's views of one model, keyed by its path relative to the atlas root. */
public final class AtlasPages {
    private AtlasPages() {}

    public static Map<String, String> render(ArchModel model) {
        return new MarkdownAtlas(model).pages();
    }
}
