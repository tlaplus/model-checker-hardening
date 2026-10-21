package io.github.tlaplus.hardening.architecture.extract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import java.util.List;
import jdk.javadoc.doclet.Doclet;

/**
 * The one doclet option, which carries {@link AtlasSettings} as JSON from {@link AtlasMain} to
 * {@link ArchitectureDoclet}.
 */
final class SettingsOption implements Doclet.Option {
    static final String NAME = "--atlas-settings";

    private AtlasSettings settings;

    /** The javadoc arguments that pass {@code settings} to the doclet. */
    static List<String> arguments(AtlasSettings settings) {
        try {
            return List.of(NAME, AtlasJson.MAPPER.writeValueAsString(settings));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cannot encode the atlas settings", e);
        }
    }

    /** The settings the javadoc tool passed. */
    AtlasSettings settings() {
        if (settings == null) {
            throw new IllegalStateException("the javadoc tool did not pass " + NAME);
        }
        return settings;
    }

    @Override
    public int getArgumentCount() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "the atlas settings, as JSON";
    }

    @Override
    public Kind getKind() {
        return Kind.STANDARD;
    }

    @Override
    public List<String> getNames() {
        return List.of(NAME);
    }

    @Override
    public String getParameters() {
        return "<json>";
    }

    @Override
    public boolean process(String option, List<String> arguments) {
        try {
            settings = AtlasJson.MAPPER.readValue(arguments.getFirst(), AtlasSettings.class);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
