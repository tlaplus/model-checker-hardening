---
state: open
labels: [apalache]
---

# `PrettyWriter` does not delimit a set-map body wrapped in a one-argument conjunction or disjunction

## Summary

Apalache's `PrettyWriter` prints a set map whose body is a one-argument `/\` or
`\/` around a membership test without parentheses. The body then reads as the
binding of a set filter. If the left operand of the membership is a name, SANY
accepts the text as a filter, which has a different value. Otherwise, SANY
rejects it with `Form {a \in b : c \in d } ... is not allowed`.

This case slipped past the fix for
[`apalache-printer-006`](apalache-printer-006.md), which adds parentheses only
when the body's top-level operator is `\in`.

Observed with `org.apalache-mc:tla-io_2.13:0.62.3-SNAPSHOT`, FuzzTLA `41bda26`,
TLC `957faa0` (tla2tools `1.8.0-20260916.164343-73`) and Apalache 0.62.2 (build
`f0dec98`).

## Reproduction

```java
var b = new TlaTypedScopeUncheckedBuilder();
var y = b.name("y", TlaTypes.INT);
var s = b.enumSet(b.integer(1));
var t = b.enumSet(b.integer(2));
var text = new TlaText(120, 2);

var direct = b.map(b.in(y, s), new ExpressionPair<>(y, t));
var and = b.map(b.and(b.in(y, s)), new ExpressionPair<>(y, t));
var or = b.map(b.or(b.in(y, s)), new ExpressionPair<>(y, t));
var rejected = b.map(b.and(b.in(b.plus(y, b.integer(0)), s)), new ExpressionPair<>(y, t));
```

`text.write` renders them as:

```text
direct:   { (y \in {1}): y \in {2} }
and:      { y \in {1}: y \in {2} }
or:       { y \in {1}: y \in {2} }
rejected: { y + 0 \in {1}: y \in {2} }
```

Only `direct` is delimited. `and` and `or` are silently reparsed. In the module
below, TLC prints `<<{}, {FALSE}>>`: the printed text is the filter
`{y \in {1} : y \in {2}}`, not the map over `{2}` that the IR describes.

```tla
---- MODULE T ----
EXTENDS Integers, TLC
VARIABLE x
Printed == { y \in {1}: y \in {2} }
Intended == { (y \in {1}): y \in {2} }
Init == x = 0 /\ PrintT(<<Printed, Intended>>)
Next == UNCHANGED x
====
```

SANY rejects the `rejected` rendering:

```text
***Parse Error***
Form {a \in b : c \in d }, at line 5, is not allowed
```

## Root cause

The `TlaSetOper.map` case of `PrettyWriter.exToDoc` checks the body's operator
itself:

```scala
val bodyDoc = body match {
  case OperEx(TlaSetOper.in, _, _) => parens(exToDoc((0, 0), body, nameResolver))
  case _                           => exToDoc((0, 0), body, nameResolver)
}
```

A body `OperEx(TlaBoolOper.and, OperEx(TlaSetOper.in, ...))` takes the second
branch. The `and`/`or` case prints a single argument without a connective. It
calls `wrapWithParen` with the parent precedence `(0, 0)`, which never adds
parentheses, and it prints the argument at the precedence of `/\`, which is
lower than that of `\in`. So the membership reaches the set braces bare.

## Occurrence

corpus28 entry `084a7bbf` failed at the parser with the forbidden-form
diagnostic. Its typed IR has a set map whose body is `AND` with a single
`SET_IN` argument, and the left operand of the membership is a `LET`
expression. The run generated modules with the settings in
`corpus28/config.toml` and mutated them with byte-level operators; this entry
came from PBT, not mutation. Entries with a name on the left of the membership
would parse and reach both checkers with different meanings, and they are not
distinguishable in the corpus without replaying the IR.

## Expected behavior

`PrettyWriter` should delimit a set-map body whenever its printed form starts
with a membership test, not only when the body's own operator is `\in`. The
fix should handle unary `/\` and `\/`, and any other wrapper that prints its
single argument unchanged. A regression test should compare the reparsed tree
with the input, since SANY accepts the corrupted form when the element is a
name.

## Impact

The parser and TLC read `PrettyWriter` output, while Apalache reads the typed IR
JSON. When SANY accepts the misprinted filter, the two checkers check different
specifications, and any resulting deviation is an artifact of the printer.
When SANY rejects the text, a valid generated module is lost at the parser
stage.
