---
state: open
labels: [apalache]
---

# `UNION` rejects a powerset-valued `CHOOSE` during bounded checking

## Summary

Apalache accepts and typechecks a `UNION` whose operand chooses the sole member
of `{SUBSET {}}`, but the bounded checker's internal type check rejects the
operand's `PowSet` arena representation. The expression is well typed and
equivalent to `UNION (SUBSET {})`, which is `{}`.

Observed in one `corpus3` input and reproduced with Apalache 0.62.2, build
`f0dec98`. See the [`28a21652...` input](../../corpus3/02apa-crash/28a216522c59d522685f553e02e1dcabe25a2a09ca7af515ddf6939b479514ea.cbor)
and its [stacktrace](../../corpus3/02apa-crash/28a216522c59d522685f553e02e1dcabe25a2a09ca7af515ddf6939b479514ea.stacktrace).

## Reproduction

```tla
---- MODULE UnionChoosePowerset ----

VARIABLE
\* @type: Set(Str);
result

\* @type: (() => Set(Str));
Rhs == UNION (CHOOSE sets \in {SUBSET {}} : TRUE)

Init == result = Rhs
Next == UNCHANGED result
Inv == TRUE

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar check \
  --init=Init --next=Next --inv=Inv --length=0 --no-deadlock \
  UnionChoosePowerset.tla
```

Snowcat reports that all expressions are typed. The bounded checker then
terminates with:

```text
internal error in type checking: Applying UNION to
CHOOSE sets$1 \in {SUBSET {}} : TRUE of type PowSet[Set(Str)]
EXITCODE: ERROR (255)
```

## Expected behavior

The bounded checker should preserve Snowcat's type for the `CHOOSE` expression
and evaluate `UNION` to the empty set. Internal arena representations must not
invalidate a source-level type-correct application.

## Impact

A small, well-typed set expression passes every front-end phase but fails before
an invariant result because the bounded checker disagrees with the front-end
typechecker.
