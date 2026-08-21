# `DefaultType1Parser` exhausts the heap on nested JSON type tags

## Summary

Apalache's JSON reader may spend exponential time and allocation parsing a
short, deeply nested type tag. A 49-character operator type containing six
nested variants exhausts a 1 GiB heap. Complete generated modules fail in
`SanyParser`, before Snowcat or bounded checking runs.

Observed with Apalache 0.62.0 and OpenJDK 25.0.3.

## Minimal reproduction

Compile this program against the Apalache release JAR:

```java
import at.forsyte.apalache.tla.types.parser.DefaultType1Parser$;

public final class Repro {
    public static void main(String[] ignored) {
        System.out.println(DefaultType1Parser$.MODULE$.apply(
                "(() => Tag1(Tag2(Tag3(Tag4(Tag5(Tag6(MODEL)))))))"));
    }
}
```

```sh
javac -cp target/apalache.jar Repro.java
java -Xmx1g -cp target/apalache.jar:. Repro
```

The process terminates with:

```text
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
```

The remaining stack is inside `DefaultType1Parser` and Scala's parser
combinators; the exact allocation site varies between runs. Five nested
variants complete under the same limit, while the sixth exhausts the heap.
This sharp increase is disproportionate to the linear growth of the input
string.

## TLA+ reproduction

The same parser is used for TLA+ type annotations. Save this module as
`NestedType.tla`:

```tla
---- MODULE NestedType ----

VARIABLE
\* @type: Tag1(Tag2(Tag3(Tag4(Tag5(Tag6(MODEL))))));
x

Init == TRUE
Next == UNCHANGED x
Inv == TRUE

====
```

Run:

```sh
java -Xmx1g -jar target/apalache.jar typecheck NestedType.tla
```

The run enters `TypeCheckerSnowcat` and exhausts the heap while parsing the
annotation. Removing one `Tag` layer makes the same command complete. The Java
reproduction above isolates the parser from both TLA+ and JSON input handling.

For typed JSON, `DefaultTagJsonReader` passes each textual `type` field to this
same parser. The failure therefore occurs in `SanyParser`, before Snowcat. A
test replacement that skipped Snowcat did not change the JSON failure because
the reader never returned the module to the next pass.

## Root cause

Apalache JSON stores every `TypeTag` as text. `DefaultTagJsonReader` reparses
each string with `DefaultType1Parser`. Its grammar starts every nested type at
the ambiguous production `operator | function | noFunExpr`, causing extensive
backtracking. The variant and row-record productions also parse their contents
once to check duplicate names, deliberately fail, and then parse them again to
construct the result.

This behavior compounds at each nesting level. A Java Flight Recorder profile
of the direct parser reproduction attributes the dominant allocation samples to
`DefaultType1Parser`, including `VarT1.parse`, `IDENT.toString`, and Scala
parser-combinator machinery. Caching repeated type strings does not solve the
problem because parsing one sufficiently nested operator type can exhaust the
heap.

## Expected behavior

Parsing a valid type should use time and memory proportional, or close to
proportional, to the type's textual size. Replace the backtracking grammar with
a deterministic or memoized implementation and add regressions for nested
variant, record, collection, function, and operator types. Include an
end-to-end JSON test so the type-reader integration cannot reintroduce the
resource failure.
