# `BoundaryInteger`

`io.github.tlaplus.hardening.gen.engine` · enum · other · [source](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/src/main/java/io/github/tlaplus/hardening/gen/engine/BoundaryInteger.java#L13) · module [gen](../modules/gen.md)

Integer values at which checker arithmetic changes behaviour (ADR 0012).

**Decisions:** [ADR 0012: Integer terminal and boundary literals](https://github.com/tlaplus/model-checker-hardening/blob/39531345378d8db5a429220f50aed4254ea670fb/docs/decisions/0012-integer-literals.md)

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
