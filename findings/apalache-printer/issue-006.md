# `PrettyWriter` does not delimit membership-valued set-map bodies

## Summary

Apalache's `PrettyWriter` can emit an ambiguous set map when the mapped body is
a membership expression. SANY interprets the body's `\in` as part of the
binding syntax that follows the colon and rejects or misparses the result.

Observed with `org.apalache-mc:tla-io_2.13:0.61.1-SNAPSHOT`.

## Minimal example

Given a set map whose body is `(~FALSE) \in S`, the writer emits:

```tla
CONSTANT S, T

Expr == { (~FALSE) \in S : x \in T }
```

SANY rejects this with:

```text
Form {a \in b : c \in d }, ... is not allowed
```

The body must be grouped independently from the map binding:

```tla
Expr == { ((~FALSE) \in S) : x \in T }
```

The corrected form passes SANY and represents the intended IR tree.

## Root cause

The `TlaSetOper.map` branch renders the body with
`exToDoc((0, 0), body, nameResolver)`. A `(0, 0)` parent never requests
parentheses, even when the body's top-level operator is `\in`. The resulting
text overlaps the grammar for set filtering and bound identifier tuples.

## Scope and impact

An exhaustive replay of 11,919 parser failures found 11 instances of this
defect. Ten produced the forbidden-form diagnostic above. One used a tuple on
the left of the body membership and failed while SANY attempted to parse it as
a tuple of bound identifiers.

## Expected behavior

`PrettyWriter` should delimit a set-map body whenever its surface syntax can be
consumed as part of the following binding. Add SANY round-trip tests for scalar
and tuple membership bodies and for nested set maps.
