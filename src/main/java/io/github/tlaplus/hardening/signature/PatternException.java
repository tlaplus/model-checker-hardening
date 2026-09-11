package io.github.tlaplus.hardening.signature;

/** A pattern that does not follow the pattern syntax, located by a one-based column. */
final class PatternException extends Exception {
    private final int column;

    PatternException(int column, String message) {
        super("column " + column + ": " + message);
        this.column = column;
    }

    int column() {
        return column;
    }
}
