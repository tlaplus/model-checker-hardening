# Apalache does not support `STRING`

Observed share: 1.00% of aggregator deviations; TLC passed and Apalache failed.

TLC recognizes the built-in set `STRING`. Apalache represents string values but
does not support the infinite set of all strings as a first-class set.

## Representative MWE

```tla
---- MODULE StringSetUnsupported ----
VARIABLE
\* @type: Str;
value
Init == value = "a"
Next == UNCHANGED value
Inv == TRUE \/ value \in STRING
====
```

TLC short-circuits the true disjunct. Apalache rejects `STRING` as unsupported.
Finite explicit sets of strings are portable.
