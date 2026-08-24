# Integer range with nonconstant bounds

Observed share: 0.16% of Apalache crash outcomes; Apalache reported its known
nonconstant-range limitation.

TLC evaluates integer ranges whose bounds depend on state. Apalache requires
both bounds of `a..b` to be constant or reducible to constants. The workflow
stored the status-255 input error as a crash, but the checker diagnostic
explicitly identifies the documented limitation.

## Representative MWE

```tla
---- MODULE NonconstantIntegerRange ----
EXTENDS Integers
VARIABLE
\* @type: Set(Int);
values
Init == values = (CHOOSE n \in {1, 2} : TRUE)..2
Next == UNCHANGED values
Inv == TRUE
====
```

TLC evaluates the deterministic choice and constructs the range. Apalache
reports `Expected a constant integer range in [ .. ]`. A bounded workaround
filters a constant superset range.
