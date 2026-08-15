# `PrettyWriter` does not delimit labels used inside larger expressions

## Summary

Apalache's `PrettyWriter` can emit unparseable TLA+ when a labelled expression
is an operand of another operator. The writer treats a label as a
high-precedence operator and omits parentheses required by the TLA+ grammar.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal example

For an IR tree whose membership element is a concatenation with a labelled
right operand, the writer emits:

```tla
EXTENDS Sequences

Expr == <<>> \o label0 :: (<<>> \o <<>>) \in {}
```

SANY rejects this with:

```text
Removing label ... would change expression parsing.
```

Grouping the complete labelled operand preserves the IR tree and passes SANY:

```tla
Expr == <<>> \o (label0 :: (<<>> \o <<>>)) \in {}
```

## Root cause

`TlaOper.label` has precedence `(16, 16)`, similar to function application.
The label branch in `PrettyWriter.exToDoc` passes this precedence to the generic
`wrapWithParen` check. It therefore does not delimit a label used as an operand
of lower-precedence syntax such as sequence concatenation.

A TLA+ label is a grammar form, not an ordinary high-precedence operator. Its
`::` body extends to the right, so omitting the delimiter can change which
expression the label decorates and how subsequent operators associate.

## Scope and impact

An exhaustive replay of 11,919 parser failures found 299 inputs with this exact
diagnostic. Missing formal parameters on labels and labels inside `EXCEPT` are
separate validity or SANY-support issues; they are not part of this printer
defect.

## Expected behavior

`PrettyWriter` should delimit a labelled expression whenever it is embedded in
another expression whose parse could extend across `::`. Add SANY round-trip
tests for labels used as operands of infix operators, membership, function
application, and other extendable expressions.
