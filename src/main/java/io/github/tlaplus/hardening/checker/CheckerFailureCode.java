package io.github.tlaplus.hardening.checker;

/** Stable failure codes shared by model-checker stages. */
public enum CheckerFailureCode {
    COUNTEREXAMPLE(12, "counterexample"),
    SPEC_EVAL(75, "spec_eval"),
    TYPECHECK(120, "typecheck"),
    PARSE(150, "parse");

    private final int encodedCode;
    private final String symbol;

    CheckerFailureCode(int encodedCode, String symbol) {
        this.encodedCode = encodedCode;
        this.symbol = symbol;
    }

    public int encodedCode() {
        return encodedCode;
    }

    public String symbol() {
        return symbol;
    }

    public static CheckerFailureCode fromEncodedCode(int encodedCode) {
        for (var code : values()) {
            if (code.encodedCode == encodedCode) {
                return code;
            }
        }
        throw new IllegalArgumentException("unsupported checker failure code: " + encodedCode);
    }
}
