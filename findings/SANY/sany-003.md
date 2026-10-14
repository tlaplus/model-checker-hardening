---
state: open
labels: [sany]
---

# SANY rejects a negated quantifier formula as binary minus with one argument

## Summary

Since tlaplus/tlaplus commit `0a20a0b` (2026-10-02), SANY rejects valid modules
in which prefix `-` is applied to a parenthesized expression that contains a
comma outside any bracket, such as a quantifier over several variables:

```tla
correction' = -(IF \A a, b \in Accounts : balance'[a] = balance'[b] THEN 0 ELSE 1)
```

Semantic analysis reports:

```text
The operator - requires 2 arguments.
```

The commit added the semantic lookahead `isMinusOpStart` to tell prefix `-(e)`
from the nonfix binary form `-(a, b)` (issue
[#884](https://github.com/tlaplus/tlaplus/issues/884)). It decides by the
presence of a comma at bracket depth zero inside the parentheses. That rule
misclassifies two kinds of operand:

1. **Commas that do not separate arguments.** Bound-variable lists
   (`\A a, b \in S`, `\E a \in S, b \in T`, `\E a, b`), `LET RECURSIVE F(_), G(_)`
   and `INSTANCE ... WITH a <- 1, b <- 2` all put a comma at depth zero.
2. **Action subscripts.** The scan counts `[` and `<<` as opening delimiters but
   not the closers `]_` and `>>_` of `[A]_v` and `<<A>>_v`. After a subscript the
   depth stays one level too high, so the scan passes the operand's `)` and
   takes the next comma in the file, even one in a later definition, as an
   argument separator.

Every release and snapshot before the commit accepts these modules. The
commit's own message names `~(\E x, y \in S: P)` as the reason to restrict the
check to `-`; the same expression under `-` is the first case above.

Observed with `org.lamport:tla2tools:1.8.0-20261013.012418-104`, tlaplus/tlaplus
revision `210df3af8aff4f6032bf9be459d5a911d079da6d`, on OpenJDK 25.0.4.1.
FuzzTLA `ea6ccb0`. Apalache 0.62.3 bundles an older SANY and accepts all
modules below.

## Reproduction

```tla
---- MODULE Accounts ----
EXTENDS Integers

CONSTANT Accounts
VARIABLE balance, correction

Init == balance = [a \in Accounts |-> 0] /\ correction = 0

Next ==
  /\ \E a \in Accounts : balance' = [balance EXCEPT ![a] = @ + 1]
  /\ correction' = -(IF \A a, b \in Accounts : balance'[a] = balance'[b] THEN 0 ELSE 1)

Spec == Init /\ [][Next]_<<balance, correction>>
====
```

```sh
java -cp tla2tools.jar tla2sany.SANY Accounts.tla
```

```text
*** Errors: 1

line 13, col 21 to line 13, col 87 of module Accounts

The operator - requires 2 arguments.
```

### Commas in the operand

Each row is the body of `X ==` in a module that extends `Integers` and
`FiniteSets` and declares `CONSTANT S` and `VARIABLE x, f`.

| Expression | Result |
| --- | --- |
| `-(IF \A a, b \in S : a = b THEN 1 ELSE 0)` | rejected |
| `-(IF \A a \in S, b \in S : a = b THEN 1 ELSE 0)` | rejected |
| `-(IF \E a, b : a = b THEN 1 ELSE 0)` | rejected |
| `-(CASE \A a, b \in S : a = b -> 1 [] OTHER -> 0)` | rejected |
| `-(CHOOSE n \in 0..5 : \E a, b \in S : a + b = n)` | rejected |
| `-(LET RECURSIVE F(_), G(_) F(n) == n G(n) == n IN F(1))` | rejected |
| `-(LET I == INSTANCE N WITH a <- 1, b <- 2 IN I!a)` | rejected |
| `x' = -(IF \A a, b \in S : a = b THEN 1 ELSE 0)` | rejected |
| `f[-(IF \A a, b \in S : a = b THEN 1 ELSE 0)]` | rejected |
| `[f EXCEPT ![1] = -(IF \A a, b \in S : a = b THEN 1 ELSE 0)]` | rejected |
| `-(IF \A a \in S : \A b \in S : a = b THEN 1 ELSE 0)` | accepted |
| `-(IF \E <<a, b>> \in S \X S : a = b THEN 1 ELSE 0)` | accepted |
| `-(Cardinality({p \in S : \E q, r \in S : q # r}))` | accepted |
| `-((IF \A a, b \in S : a = b THEN 1 ELSE 0))` | accepted |
| `0 - (IF \A a, b \in S : a = b THEN 1 ELSE 0)` | accepted |
| `~(\E a, b \in S : a = b)` | accepted |
| `-(1, 2)` | accepted (binary minus) |

### Action subscripts in the operand

| Expression | Result |
| --- | --- |
| `<<-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2>>` | rejected |
| `<<-(IF ENABLED <<x' = 1>>_x THEN 1 ELSE 0), 2>>` | rejected |
| `{-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2}` | rejected |
| `[a \|-> -(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), b \|-> 2]` | rejected |
| `<<-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0)>>` | accepted |
| `<<(-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0)), 2>>` | accepted |

The comma need not belong to the expression. This module fails at `Neg`
because of the comma in `Same`; writing `Same` as
`\A a \in {1} : \A b \in {1} : a = b` makes it parse:

```tla
---- MODULE Later ----
EXTENDS Integers

VARIABLE x

Neg == -(IF ENABLED [x' = 1]_x THEN 1 ELSE 0)
Same == \A a, b \in {1} : a = b

====
```

Wrapping the negation in parentheses hides one subscript, because the extra
`)` absorbs the extra depth level. It does not hide two.

### Affected versions

Both `X == -(IF \A a, b \in S : a = b THEN 1 ELSE 0)` and
`X == <<-(IF ENABLED [x' = 1]_x THEN 1 ELSE 0), 2>>`:

| tla2tools | Result |
| --- | --- |
| v1.7.4, v1.8.0 | accepted |
| 1.8.0 snapshots 20260731 to 20261001.024819-90 | accepted |
| VS Code extension 2026.9.251644 | accepted |
| 1.8.0-20261006.015956-102 | rejected |
| 1.8.0-20261013.012418-104 (`210df3a`) | rejected |
| VS Code extension 2026.10.40311 (`1813307`) | rejected |

## Root cause

`isMinusOpStart` in `tlatools/org.lamport.tlatools/javacc/tla+.jj` (lines
425-464 at `210df3a`) guards the prefix alternatives of `Expression()` and
`ExtendableExpr()`. It scans the tokens after `-(`:

```java
case LBR: case LSB: case LBC: case LAB:
  depth++; break;
case RBR: case RSB: case RBC: case RAB:
  if ( depth == 0 ) return false;
  depth--; break;
case COMMA:
  if ( depth == 0 ) return true; break;
```

A comma at depth zero is not evidence of an argument list: the grammar allows
one inside a single expression wherever it lists bound variables, recursive
declarations or substitutions. When the scan returns `true`, the parser reads
`-(...)` as an operator application, parses the whole parenthesized text as one
argument, and semantic analysis rejects the arity.

Separately, `[A]_v` closes with `ARSB` (`"]_"`) and `<<A>>_v` with `ARAB`
(`">>_"`), which the second case omits.

Two possible fixes, neither built and tested here:

- Replace the token scan with a syntactic lookahead that commits to the
  nonfix form only if `<LBR> Expression() <COMMA>` parses. `Expression()`
  consumes the commas of a quantifier, so only an argument separator remains.
- At minimum, add `ARSB` and `ARAB` to the closing case. This fixes the
  subscripts but not the commas.

## Corpus evidence

`corpus61` entry `e746c015` is a metamorphic module whose rewritten operator
applies `NegNeg` to an operand with two `ENABLED [...]_FALSE` subexpressions,
inside a set enumeration with a following element. SANY rejected it with this
diagnostic; it is the corpus's only instance. FuzzTLA cannot produce the comma
form: its IR has one bound variable per quantifier and the printer writes it
that way.

## Expected behavior and impact

`-(e)` with a single operand is the prefix minus regardless of what `e`
contains. SANY should accept every module above, as it did before `0a20a0b`.

Every tool that parses with the current SANY rejects these modules: TLC, the
TLA+ Toolbox and the VS Code extension. The comma form occurs in ordinary
specifications that negate a conditional over a quantifier. The diagnostic
names an arity, not the comma, and in the subscript form points at a
definition other than the one with the comma. Until a fix lands, writing
`0 - (e)` or `-((e))` avoids it.
