package io.github.tlaplus.hardening.corpus;

import java.util.Arrays;
import java.util.Objects;

/** The required fields of one encoded corpus input. */
public record CorpusInput(Kind kind, byte[] input) {
    public CorpusInput {
        Objects.requireNonNull(kind, "kind");
        input = Objects.requireNonNull(input, "input").clone();
    }

    /** Creates an input consumed by the expression IR generator. */
    public static CorpusInput expression(byte[] input) {
        return new CorpusInput(Kind.EXPRESSION, input);
    }

    /** Returns an independent copy of the generator input bytes. */
    @Override
    public byte[] input() {
        return input.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof CorpusInput that
                        && kind == that.kind
                        && Arrays.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + Arrays.hashCode(input);
    }

    /** Input kinds defined by the corpus format. */
    public enum Kind {
        EXPRESSION("expr"),
        MODULE("module");

        private final String encodedName;

        Kind(String encodedName) {
            this.encodedName = encodedName;
        }

        /** Returns the string stored in the CBOR {@code kind} field. */
        public String encodedName() {
            return encodedName;
        }

        static Kind fromEncodedName(String encodedName) throws CorpusInputFormatException {
            return switch (encodedName) {
                case "expr" -> EXPRESSION;
                case "module" -> MODULE;
                default -> throw new CorpusInputFormatException(
                        "unknown input kind: " + encodedName);
            };
        }
    }
}
