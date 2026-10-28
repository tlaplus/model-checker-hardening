package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.Technique;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Reads the {@code --how} option of {@code fuzztla init} by the technique's encoded name. */
public final class TechniqueConverter implements ITypeConverter<Technique> {
    /** The option's description, shared by every command that takes it. */
    static final String DESCRIPTION = "Fuzzing technique: pbt, mt for metamorphic testing (ADR 0016), or gbt for"
            + " graph-based testing (ADR 0023).";

    @Override
    public Technique convert(String value) {
        return Technique.fromEncodedName(value).orElseThrow(() -> new TypeConversionException(
                "expected one of: " + String.join(", ", Technique.encodedNames())));
    }
}
