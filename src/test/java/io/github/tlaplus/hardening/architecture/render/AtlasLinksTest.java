package io.github.tlaplus.hardening.architecture.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.architecture.model.ArchModel.Source;
import org.junit.jupiter.api.Test;

class AtlasLinksTest {
    @Test
    void linksToGitHub() {
        var links = new AtlasLinks(new Source("https://github.com/tlaplus/model-checker-hardening",
                "0123456789abcdef0123456789abcdef01234567"));
        assertEquals("https://tlaplus.github.io/model-checker-hardening/dev/", links.explorer());
        assertEquals("0123456", links.shortCommit());
        var type = RenderFixtures.store("io.github.tlaplus.hardening.corpus.CorpusLock");
        assertEquals("https://github.com/tlaplus/model-checker-hardening/blob/"
                + "0123456789abcdef0123456789abcdef01234567/src/CorpusLock.java#L12", links.source(type));
        assertEquals("main", new AtlasLinks(new Source("https://github.com/a/b", "main")).shortCommit());
    }
}
