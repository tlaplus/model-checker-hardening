# `BoundaryInteger`

`io.github.tlaplus.hardening.gen.engine` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/2c6aced743b4276ca683c29709daab946d1b9536/src/main/java/io/github/tlaplus/hardening/gen/engine/BoundaryInteger.java#L13) · module [gen](../modules/gen.md)

Integer values at which checker arithmetic changes behaviour (ADR 0012).

**Decisions:** [ADR 0012: Integer terminal and boundary literals](https://github.com/tlaplus/model-checker-hardening/blob/2c6aced743b4276ca683c29709daab946d1b9536/docs/decisions/0012-integer-literals.md)

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
