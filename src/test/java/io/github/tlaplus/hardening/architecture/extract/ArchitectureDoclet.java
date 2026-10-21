package io.github.tlaplus.hardening.architecture.extract;

import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * The doclet that extracts the atlas model of the main sources and writes it as JSON (ADR 0022).
 * {@link ModelExtraction} runs it.
 */
public final class ArchitectureDoclet implements Doclet {
    private final SettingsOption settings = new SettingsOption();
    private Reporter reporter;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "atlas";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of(settings);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        try {
            var model = ModelBuilder.build(environment, settings.settings());
            AtlasJson.write(model, settings.settings().output().resolve(AtlasJson.MODEL_FILE));
            return true;
        } catch (IOException | RuntimeException e) {
            reporter.print(Diagnostic.Kind.ERROR, "atlas: " + e);
            return false;
        }
    }
}
