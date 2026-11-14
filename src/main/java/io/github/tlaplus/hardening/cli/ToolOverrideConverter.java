package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.ToolJar;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Reads a {@code --tool TOOL=FILE} option of {@code fuzztla init}, TOOL being a {@code [tools]} key. */
final class ToolOverrideConverter implements ITypeConverter<ToolOverride> {
    @Override
    public ToolOverride convert(String value) {
        var separator = value.indexOf('=');
        if (separator < 0 || separator == value.length() - 1) {
            throw new TypeConversionException("expected TOOL=FILE");
        }
        var tool = ToolJar.fromConfigKey(value.substring(0, separator)).orElseThrow(() -> new TypeConversionException(
                "expected TOOL to be one of: " + String.join(", ", ToolJar.configKeys())));
        try {
            return new ToolOverride(tool, Path.of(value.substring(separator + 1)));
        } catch (InvalidPathException exception) {
            throw new TypeConversionException("invalid FILE: " + exception.getMessage());
        }
    }
}
