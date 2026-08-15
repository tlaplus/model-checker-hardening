# `PrettyWriter` emits malformed action and fairness syntax

## Summary

Apalache's `PrettyWriter` emits invalid TLA+ for non-stuttering actions and for
action or fairness expressions whose subscripts require explicit grouping. The
specialized rendering paths use the wrong delimiters or rely on operator
precedence where the TLA+ grammar requires a restricted expression.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal examples

For the IR expression `nostutter(FALSE, FALSE)`, the writer emits:

```tla
<FALSE>_FALSE
```

TLA+ non-stuttering action syntax requires double angle brackets:

```tla
<<FALSE>>_FALSE
```

For subscripts that do not fit the unparenthesized restricted form, the writer
can emit:

```tla
[FALSE]_Head(<<>>)
WF_0(FALSE)
SF_0(FALSE)
```

The subscript expressions must be delimited:

```tla
[FALSE]_(Head(<<>>))
WF_(0)(FALSE)
SF_(0)(FALSE)
```

The emitted forms produce syntax, fairness-linking, or apparent operator-arity
errors in SANY. A module containing all four corrected forms passes SANY parsing
and semantic analysis.

## Root cause

The `TlaActionOper.nostutter` branch uses the pretty-printing `angles` helper,
which emits one `<` and one `>`, although TLA+ requires `<<` and `>>` around the
action.

The adjacent `stutter`, `nostutter`, `weakFairness`, and `strongFairness`
branches render the subscript with `exToDoc(op.precedence, vars, ...)`. Generic
precedence wrapping is insufficient after `_`, where SANY expects a restricted
expression. Expressions such as `Head(<<>>)` after an action subscript or `0`
after `WF_` or `SF_` must be parenthesized as complete subscripts.

These defects belong to one grammar-specific action and fairness rendering
group: both specialized branches must emit the delimiters required by the TLA+
surface grammar rather than infer them only from IR precedence.

## Scope and impact

A conservative classification of a deterministic 5,000-entry replay assigned
1,346 failures (26.92%) to direct action or fairness signatures. They included
malformed angle-action tokens, malformed `]_` subscripts, `WF_` and `SF_`
restricted-expression errors, fairness-linking errors, and subscript names that
SANY misinterpreted as undeclared operators.

## Expected behavior

`PrettyWriter` should use `<<A>>_v` for `nostutter` and delimit every subscript
that is not valid in an unparenthesized restricted position in `[A]_v`,
`<<A>>_v`, `WF_v(A)`, and `SF_v(A)`. Add SANY round-trip tests for all four
operators with both bare-name and explicitly grouped subscripts.
