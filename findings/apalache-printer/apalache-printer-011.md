---
state: open
labels: [apalache]
---

# `PrettyWriter` nests `LET` definitions of one name by moving them out of operator arguments

## Summary

Apalache's `PrettyWriter` moves every `LET` definition of an operator's arguments
in front of the application. If two arguments define the same name, or one
argument repeats a definition that another argument contains, the moved
definitions end up nested. SANY rejects the printed module with `Operator L
already defined or declared`, although the IR is valid TLA<sup>+</sup>: sibling
`LET` definitions of one name do not overlap in scope.

Observed with `org.apalache-mc:tla-io_2.13:0.62.3-SNAPSHOT`, FuzzTLA `b7caaf2`,
TLC `0dab95e` (tla2tools `1.8.0-20261001.024819-90`) and Apalache 0.62.2.
Reproduced with `tla-io_2.13:0.62.4-SNAPSHOT` and FuzzTLA `e9f9247`. The code is
unchanged in Apalache 0.62.3 (`3eb15b2`) and `8677897`.

## Reproduction

```java
var b = new TlaTypedScopeUncheckedBuilder();
var l = b.letIn(b.seq(b.integer(1), b.integer(2)), b.decl("L", b.bool(false)));
var text = new TlaText(80, 0);

var head = b.head(l);
var sum = b.plus(b.len(l), b.len(l));
var subSeq = b.subSeq(l, b.integer(1), b.len(l));
```

`text.write` renders them as:

```text
head:   (LET L == FALSE IN Head(<<1, 2>>))
sum:    (LET L == FALSE IN Len(<<1, 2>>)) + (LET L == FALSE IN Len(<<1, 2>>))
subSeq: (LET L == FALSE IN SubSeq(<<1, 2>>, 1, ((LET L == FALSE IN Len(<<1, 2>>)))))
```

`head` and `sum` are equivalent to the IR. In `subSeq`, the definition of the
first argument now encloses the third, so its copy of `L` is nested. SANY
rejects it:

```text
Semantic errors:
*** Errors: 1
line 3, col 57 to line 3, col 66 of module LetNest
Operator L already defined or declared.
```

SANY accepts the IR printed literally:

```tla
SubSeq(LET L == FALSE IN <<1, 2>>, 1, Len(LET L == FALSE IN <<1, 2>>))
```

## Root cause

The two `OperEx` cases of `PrettyWriter.exToDoc` that print an application by
name call `extractDecls(args)`. It takes the definitions of every `LET`
argument other than a `LAMBDA`, prints the argument's body in its place, and
`letToDocInParens` prints the definitions in front of the application. Its
comment gives the reason:

```scala
/**
 * On TLA+ files, we can't have LET..IN expressions as arguments. ...
 */
def extractDecls(exprs: Seq[TlaEx]): (List[Doc], Seq[TlaEx]) = {
```

TLA<sup>+</sup> allows `LET` as an argument, and SANY accepts it. Moving a
definition widens its scope from one argument to the whole application. The
widened scope overlaps another definition of the same name, whether in a
sibling argument or nested inside one.

## Occurrence

corpus57 is a metamorphic corpus. In entry `1288dc84`, `SubSeqWhole` rewrote
`LET LocalOp2 == FALSE IN <<...>>` to `SubSeq(s, 1, Len(s))`, which copies the
definition into two arguments of one `SubSeq`. Its typed IR has sibling copies.
SANY rejected the printed module with `Operator LocalOp2 already defined or
declared`. Generated modules name every definition uniquely, so only copies
made by rewrites reach this. FuzzTLA `e9f9247` renames copied definitions apart
and so avoids it.

## Expected behavior

`PrettyWriter` should print a `LET` argument in place, in parentheses, as it
already does for nested `LET` expressions elsewhere. If moving the definitions
is kept, it must rename a definition apart when its name is already defined
in the widened scope. A regression test should print `SubSeq` with two
argument `LET`s of one name and parse the result with SANY.

## Impact

The parser and TLC read `PrettyWriter` output. A valid module whose
applications copy a `LET`-carrying argument is lost at the parser stage. Any IR
transformation that duplicates subterms can produce such modules.
