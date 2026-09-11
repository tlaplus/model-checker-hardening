package io.github.tlaplus.hardening.signature;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parses the S-expression pattern syntax that the known-defect manual specifies:
 *
 * <pre>
 * pattern := _ | ?name | integer | "string" | TRUE | FALSE | STRING | Int | Nat | BOOLEAN
 *          | identifier | (OPER pattern* [...]) | (: pattern "Type1")
 * </pre>
 *
 * <p>Operator names are checked against {@link OperatorNames} and types are parsed here, so a
 * misspelled operator or malformed type is a load error rather than a signature that silently
 * never matches.
 */
final class PatternParser {
    private static final String WILDCARD = "_";
    private static final String ELLIPSIS = "...";
    private static final String TYPE_CONSTRAINT = ":";
    private static final char METAVARIABLE = '?';
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_!$]*");
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private final String text;
    private int position;

    private PatternParser(String text) {
        this.text = text;
    }

    /** Parses one complete pattern. */
    static IrPattern parse(String text) throws PatternException {
        var parser = new PatternParser(Objects.requireNonNull(text, "text"));
        var pattern = parser.pattern();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected text after the pattern");
        }
        return pattern;
    }

    private IrPattern pattern() throws PatternException {
        skipWhitespace();
        if (atEnd()) {
            throw error("expected a pattern");
        }
        return switch (text.charAt(position)) {
            case '(' -> application();
            case ')' -> throw error("unexpected ')'");
            case '"' -> new IrPattern.StringLiteral(string());
            default -> atom();
        };
    }

    private IrPattern application() throws PatternException {
        var open = position;
        position++;
        skipWhitespace();
        var headColumn = column();
        var head = token();
        if (head.isEmpty()) {
            throw error("expected an operator name after '('");
        }
        if (head.equals(TYPE_CONSTRAINT)) {
            return typed(open);
        }
        var operator = OperatorNames.find(head)
                .orElseThrow(() -> new PatternException(headColumn, "unknown operator '" + head + "'"));
        var arguments = new ArrayList<IrPattern>();
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                throw new PatternException(open + 1, "missing ')'");
            }
            if (text.charAt(position) == ')') {
                position++;
                return new IrPattern.Application(operator, arguments, false);
            }
            if (text.startsWith(ELLIPSIS, position)) {
                var ellipsis = position;
                position += ELLIPSIS.length();
                skipWhitespace();
                if (atEnd() || text.charAt(position) != ')') {
                    throw new PatternException(ellipsis + 1, "'...' must end the arguments");
                }
                position++;
                return new IrPattern.Application(operator, arguments, true);
            }
            arguments.add(pattern());
        }
    }

    private IrPattern typed(int open) throws PatternException {
        var pattern = pattern();
        skipWhitespace();
        if (atEnd() || text.charAt(position) != '"') {
            throw error("expected a quoted type after the pattern");
        }
        var typeColumn = column();
        var type = string();
        skipWhitespace();
        if (atEnd() || text.charAt(position) != ')') {
            throw new PatternException(open + 1, "a type constraint takes one pattern and one type");
        }
        position++;
        try {
            return new IrPattern.Typed(pattern, TypePattern.parse(type));
        } catch (IllegalArgumentException exception) {
            throw new PatternException(typeColumn, exception.getMessage());
        }
    }

    private IrPattern atom() throws PatternException {
        var column = column();
        var atom = token();
        if (atom.equals(WILDCARD)) {
            return new IrPattern.Wildcard();
        }
        if (atom.equals(ELLIPSIS)) {
            throw new PatternException(column, "'...' may only end an operator's arguments");
        }
        if (atom.charAt(0) == METAVARIABLE) {
            var name = atom.substring(1);
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new PatternException(column, "malformed metavariable '" + atom + "'");
            }
            return new IrPattern.MetaVariable(name);
        }
        if (INTEGER.matcher(atom).matches()) {
            return new IrPattern.IntegerLiteral(new BigInteger(atom));
        }
        if (atom.equals("TRUE") || atom.equals("FALSE")) {
            return new IrPattern.BooleanLiteral(atom.equals("TRUE"));
        }
        var set = PredefinedSet.bySpelling(atom);
        if (set.isPresent()) {
            return new IrPattern.PredefinedSetLiteral(set.orElseThrow());
        }
        if (IDENTIFIER.matcher(atom).matches()) {
            return new IrPattern.Name(atom);
        }
        throw new PatternException(column, "unexpected '" + atom + "'");
    }

    /** Reads a double-quoted string, in which only {@code \"} and {@code \\} are escapes. */
    private String string() throws PatternException {
        var open = position;
        position++;
        var result = new StringBuilder();
        while (!atEnd()) {
            var character = text.charAt(position++);
            if (character == '"') {
                return result.toString();
            }
            if (character == '\\') {
                if (atEnd() || (text.charAt(position) != '"' && text.charAt(position) != '\\')) {
                    throw new PatternException(position, "unsupported escape in a string");
                }
                character = text.charAt(position++);
            }
            result.append(character);
        }
        throw new PatternException(open + 1, "unterminated string");
    }

    /** Reads the characters up to whitespace, a parenthesis, or a quote. */
    private String token() {
        var start = position;
        while (!atEnd()) {
            var character = text.charAt(position);
            if (Character.isWhitespace(character)
                    || character == '(' || character == ')' || character == '"') {
                break;
            }
            position++;
        }
        return text.substring(start, position);
    }

    private void skipWhitespace() {
        while (!atEnd() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean atEnd() {
        return position >= text.length();
    }

    private int column() {
        return position + 1;
    }

    private PatternException error(String message) {
        return new PatternException(column(), message);
    }
}
