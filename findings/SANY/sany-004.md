---
state: open
labels: [sany]
---

# SANY rejects a tuple containing `<<A>>_v` after `[` or `{` when `\in` follows a later `>>`

## Summary

SANY rejects valid modules in which `[` or `{` is followed by a tuple that
contains an action subscript `<<A>>_v`, if a later `>>` in the text is followed
by `\in`:

```tla
Inv == << [<< ENABLED <<x' = 1 - x>>_x >> EXCEPT ![1] = FALSE] >> \in {<<<<FALSE>>>>}
```

Parsing reports:

```text
***Parse Error***
Was expecting "Identifier or >>"
```

It is a parse error only. SANY stops before semantic analysis and level
checking, and the module is rejected by every tool that parses with SANY:
TLC, the Toolbox, the VS Code extension and Apalache. The semantic lookahead
`matchFcnConst` causes it. That lookahead is already in the first commit of
`tla+.jj` in tlaplus/tlaplus (`6624982bd`, 2009), so the defect is likely as
old as SANY.

Observed with TLC `7e64fd0` (tlaplus/tlaplus
`7e64fd0376ce8a3d9087fc267b917564df7e2c3a`, 2026-02-23) and `bc1b506`
(`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`, 2026-11-14, snapshot
`1.8.0-20261115.003216-126`), and with the SANY bundled in Apalache 0.63.0
(release JAR), on OpenJDK 25.0.5.1. FuzzTLA `a410726a`.

## Reproduction

```tla
---- MODULE EnabledTuple ----
EXTENDS Integers
VARIABLE x

Init == x = 0
Next == x' = 1 - x
Spec == Init /\ [][Next]_x

Inv == << [<< ENABLED <<x' = 1 - x>>_x >> EXCEPT ![1] = FALSE] >> \in {<<<<FALSE>>>>}
====
```

```sh
java -cp tla2tools.jar tlc2.TLC -workers 1 EnabledTuple   # SPECIFICATION Spec, INVARIANT Inv
```

```text
***Parse Error***
Was expecting "Identifier or >>"
Encountered "ENABLED" at line 9, column 15 and token "<<"
Residual stack trace follows:
Identifier tuple starting at line 9, column 12.
Quant Bound starting at line 9, column 12.
Some [] Form starting at line 9, column 11.
```

TLC exits 150 on both versions. With the inner tuple in parentheses,
`[(<< ENABLED <<x' = 1 - x>>_x >>) EXCEPT ![1] = FALSE]`, both versions check
the model and find no error (2 distinct states, exit 0).

### Variants

Each row is the body of `X ==` in a module that declares `VARIABLE x, f` and
defines `S == {<<<<TRUE>>>>}`. The two versions agree on every row.

| Expression | Result |
| --- | --- |
| `<< [<< <<x' = x>>_x >> EXCEPT ![1] = FALSE] >> \in S` | rejected |
| `<< [<< TRUE, <<x' = x>>_x >> EXCEPT ![1] = FALSE] >> \in S` | rejected |
| `<< [<< (<<x' = x>>_x) >> EXCEPT ![1] = FALSE] >> \in S` | rejected |
| `<< [<< <<x' = x>>_x >> -> BOOLEAN] >> \in S` | rejected |
| `<< f[<< <<x' = x>>_x >>] >> \in S` | rejected |
| `<< {<< <<x' = x>>_x >>, <<TRUE>>} >> \in S` | rejected |
| `<< {<< <<x' = x>>_x >> : y \in BOOLEAN} >> \in S` | rejected |
| `<< << [<< <<x' = x>>_x, <<x' = x>>_x >> EXCEPT ![1] = FALSE] >> >> \in S` | rejected |
| `<< [<< x' = x >> EXCEPT ![1] = FALSE] >> \in S` | accepted |
| `<< [<< [x' = x]_x >> EXCEPT ![1] = FALSE] >> \in S` | accepted |
| `<< [<< <<x' = x>>_x >> EXCEPT ![1] = FALSE] >> = S` | accepted |
| `<< << [<< <<x' = x>>_x >> EXCEPT ![1] = FALSE] >> >> \in S` | accepted |
| `<< [(<< <<x' = x>>_x >>) EXCEPT ![1] = FALSE] >> \in S` | accepted |
| `<< (<< <<x' = x>>_x >>) >> \in S` | accepted |

Each subscript inside the tuple needs one more enclosing `>>` before the
`\in`, as the two rows with doubled tuples show. `[A]_v` does not trigger the
defect.

## Root cause

`SBracketCases` and `BraceCases` in
`tlatools/org.lamport.tlatools/javacc/tla+.jj` guard the function constructor
`[<<x, y>> \in S |-> e]` and the set comprehension `{<<x, y>> \in S : p}` with
the semantic lookahead `matchFcnConst` (lines 545-578 at `bc1b506`). After
`<<` it scans for the matching `>>` and tests whether `\in` follows:

```java
if (t.kind == LAB) {
  int count = 1;
  while (count != 0 ) {
    t = getNextPreviewToken( t );
    if (t.kind == LAB) count++;
    if (t.kind == RAB) count--;
    if (t.kind == EOF) return false;
  }
  t = getNextPreviewToken( t );
  return (t.kind == IN );
}
```

`<<A>>_v` closes with the token `ARAB` (`">>_"`), not `RAB`, so the scan never
counts it. After a subscript, the count stays one level too high. The scan
then skips the tuple's own `>>` and stops at an enclosing `>>`. If `\in`
follows that one, the lookahead succeeds, and the parser commits to
`QuantBound()`, whose `IdentifierTuple()` rejects the first token that is not
an identifier.

Only rejections result. An identifier tuple cannot contain `>>_`, so the
miscount never hides a genuine function constructor or comprehension.
[sany-003](sany-003.md) records the same omission of `ARAB` (and `ARSB`) in a
different scan, `isMinusOpStart`.

Counting `ARAB` as a closer (`t.kind == RAB || t.kind == ARAB`) fixes the
miscount. This fix has not been built and tested here. The lookahead would
still misread `{<<1, 2>> \in S}`, which the function's own comment notes.

## Corpus evidence

`corpus87` entry `d701237a` is the only one of its 211 parser failures with
this diagnostic. Its `Inv` is a tuple, tested with `\in {TRUE}`. Deep inside
it, `ApaFoldSeqLeft(Lambda9, [<< [<< … >> …] >> EXCEPT …], …)` has one
`ENABLED (<<…>>_(…))` in the inner tuple. Rewriting only that `>>_(` as
`>> = (` makes the entry parse on both versions.
