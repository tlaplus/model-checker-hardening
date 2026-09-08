# Integer outside TLC's supported range

Observed share: 0.41% of aggregator deviations; TLC failed and Apalache passed.

TLC stores integers in a Java `int` and rejects values outside the signed
32-bit range. Apalache's integer encoding is not restricted to that range.

## Representative MWE

```tla
---- MODULE IntegerOutsideTlcRange ----
EXTENDS Integers
VARIABLE
\* @type: Int;
result
Init == result = 2147483648
Next == UNCHANGED result
Inv == TRUE
====
```

TLC reports `TLC_INTEGER_TOO_BIG`; Apalache accepts the value. Corpus3 also
contains three exponentiations that overflow TLC's integer representation, and
corpus8 adds a multiplication (`Overflow when computing -657264081*84`); they
produce the same capability difference. The overflow is reported for whichever
operator computes the out-of-range result, so the triage signature matches the
`Overflow when computing` prefix rather than one operator. This is a known
TLC capability limit, confirmed by the corpus.
