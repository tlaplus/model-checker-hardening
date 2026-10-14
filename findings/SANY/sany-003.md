---
state: open
labels: [sany]
---

# SANY parses a unary minus over an action subscript as binary minus

## Summary

SANY rejects valid modules in which a prefix `-` is applied to a parenthesized
expression that contains an action subscript, `[A]_v` or `<<A>>_v`, and a comma
follows later in the input. Semantic analysis reports:

```text
The operator - requires 2 arguments.
```

The parser decides between prefix `-(e)` and the nonfix binary form `-(a, b)`
with the semantic lookahead `isMinusOpStart`. The lookahead counts `[` and
`<<` as opening delimiters but does not count the subscript closers `]_` and
`>>_` as closing ones. Each subscript leaves the nesting depth one level too
high, so the scan does not stop at the `)` that closes the operand. It runs on
and takes the next comma at depth zero, which may lie outside the expression or
in a later definition, as evidence for the binary form.

Observed with `org.lamport:tla2tools:1.8.0-20261013.012418-104`, tlaplus/tlaplus
revision `210df3af8aff4f6032bf9be459d5a911d079da6d`, on OpenJDK 25.0.4.1, and
with the VS Code extension's bundled tla2tools (revision `1813307`). FuzzTLA
`ea6ccb0`. Apalache was not involved.

## Reproduction

```tla
---- MODULE MinusSubscript ----
EXTENDS Integers

VARIABLE x

Pair == <<-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2>>

====
```

```sh
java -cp tla2tools.jar tla2sany.SANY MinusSubscript.tla
```

```text
Semantic errors:

*** Errors: 1

line 6, col 12 to line 6, col 48 of module MinusSubscript

The operator - requires 2 arguments.
```

The same expression is accepted without the following tuple element. The comma
need not belong to the expression at all. This module fails at `Neg` because of
the comma in `Same`:

```tla
---- MODULE Later ----
EXTENDS Integers

VARIABLE x

Neg == -(IF ENABLED [x' = 1]_x THEN 1 ELSE 0)
Same == \A a, b \in {1} : a = b

====
```

Writing `Same` as `\A a \in {1} : \A b \in {1} : a = b` makes the module parse.

| Expression in `Pair` | Result |
| --- | --- |
| `<<-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2>>` | rejected |
| `<<-(IF ENABLED <<x' = 1>>_x THEN 1 ELSE 0), 2>>` | rejected |
| `{-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2}` | rejected |
| `[a \|-> -(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), b \|-> 2]` | rejected |
| `<<(-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0)), 2>>` | accepted |
| `<<0 - (IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2>>` | accepted |
| `<<-(IF TRUE THEN 1 ELSE 0), 2>>` | accepted |

Parenthesizing the negation hides one subscript, because the extra `(` and `)`
absorb the extra depth level. With two subscripts in the operand it does not.
The corpus input below is such a case.

## Root cause

`isMinusOpStart` in `tlatools/org.lamport.tlatools/javacc/tla+.jj` (lines
425-464 at `210df3a`), added for
[tlaplus/tlaplus#884](https://github.com/tlaplus/tlaplus/issues/884), scans the
tokens after `-(`:

```java
case LBR: case LSB: case LBC: case LAB:
  depth++; break;
case RBR: case RSB: case RBC: case RAB:
  if ( depth == 0 ) return false;
  depth--; break;
case COMMA:
  if ( depth == 0 ) return true; break;
```

`[A]_v` opens with `LSB` and closes with `ARSB` (`"]_"`); `<<A>>_v` opens with
`LAB` and closes with `ARAB` (`">>_"`). Neither closer is in the second case.
For `-([A]_v), 2` the scan reaches depth 1 at `[`, keeps it at `]_`, drops to 0
at the operand's `)` instead of returning `false`, and returns `true` at the
comma. `Expression()` and `ExtendableExpr()` then skip the prefix alternative,
the parser builds the nonfix application `-(e)` with one argument, and semantic
analysis rejects its arity.

Adding `ARSB` and `ARAB` to the closing case should make the scan balance
subscripts (not built and tested here):

```java
case RBR: case RSB: case ARSB: case RBC: case RAB: case ARAB:
```

## Corpus evidence

`corpus61` entry `e746c015` is a metamorphic module whose rewritten operator
applies `NegNeg` to an operand with two `ENABLED [...]_FALSE` subexpressions,
inside a set enumeration with a following element. SANY rejected it with this
diagnostic; it is the corpus's only instance.

## Expected behavior and impact

`-(e)` with a single operand is the prefix minus regardless of what `e`
contains. SANY should accept every module above.

Any tool that uses SANY rejects these modules: TLC, Apalache and the TLA+
Toolbox. The defect needs an action subscript under a prefix minus, which is
rare in hand-written specifications, where it is usually an `ENABLED` guard.
A failing module is reported with a misleading arity error, and the comma that
triggers it can be in an unrelated definition. Generators that target
SANY-valid source can avoid it by writing `0 - e` for a negation whose operand
contains an action subscript.
