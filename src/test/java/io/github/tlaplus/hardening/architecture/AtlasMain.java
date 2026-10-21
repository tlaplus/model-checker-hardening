package io.github.tlaplus.hardening.architecture;

import io.github.tlaplus.hardening.architecture.extract.AtlasSettings;
import io.github.tlaplus.hardening.architecture.extract.ModelExtraction;
import io.github.tlaplus.hardening.architecture.render.AtlasPages;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/**
 * Builds the architecture atlas (ADR 0022): extracts the model of the main sources, then renders
 * its views next to it.
 *
 * <p>Usage: {@code AtlasMain [--output DIR] [--commit COMMIT] [--repository URL]}, from the
 * repository root, after the main classes are compiled.
 */
public final class AtlasMain {
    private AtlasMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("option " + args[args.length - 1] + " needs a value");
        }
        var settings = AtlasSettings.defaults(Path.of("").toAbsolutePath());
        for (var i = 0; i < args.length; i += 2) {
            var value = args[i + 1];
            settings = switch (args[i]) {
                case "--output" -> settings.withOutput(Path.of(value).toAbsolutePath());
                case "--commit" -> settings.withCommit(value);
                case "--repository" -> settings.withRepository(value);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            };
        }
        run(settings);
        System.out.println("atlas written to " + settings.output());
    }

    /** Builds the atlas that {@code settings} describe into their output directory. */
    public static void run(AtlasSettings settings) throws IOException {
        AtlasOutput.prepare(settings.output());
        var model = ModelExtraction.extract(settings);
        AtlasOutput.write(settings.output(), AtlasPages.render(model));
    }
}
