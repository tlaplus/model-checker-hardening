# `BoundaryInteger`

`io.github.tlaplus.hardening.gen.engine` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/4df6a11e5b4bf93609393935c4cbeceec08cce0c/src/main/java/io/github/tlaplus/hardening/gen/engine/BoundaryInteger.java#L13) · module [gen](../modules/gen.md)

Integer values at which checker arithmetic changes behaviour (ADR 0012).

**Decisions:** [ADR 0012: Integer terminal and boundary literals](https://github.com/tlaplus/model-checker-hardening/blob/4df6a11e5b4bf93609393935c4cbeceec08cce0c/docs/decisions/0012-integer-literals.md)

```mermaid
classDiagram
  class BoundaryInteger {
    <<enum>>
  }
  class IntegerExprGenFactory
  IntegerExprGenFactory ..> BoundaryInteger : uses
```

| Direction | Relation | Type | Summary |
|---|---|---|---|
| ← | uses | [IntegerExprGenFactory](io.github.tlaplus.hardening.gen.engine.IntegerExprGenFactory.md) | Constructs integer-valued expression generators. |
