# Actions, temporal properties and fairness

> **Status:** Implemented.
> [ADR 0007](../decisions/0007-levels-and-temporal-properties.md) records the
> design and the checker measurements behind it.

A `module` corpus can generate primed guards in `Next`, a temporal property
checked by both model checkers, and fairness conditions in the specification.
Generation follows the TLA<sup>+</sup> level rules of *Specifying Systems*, so
SANY accepts every such module. Shapes that TLC cannot check are generated too,
and the shipped known-defect signatures quarantine them (section 5).

## 1. Enabling the operators

In the corpus's `config.toml`, remove `action` and `temporal` from the ignore
list:

```toml
[generator]
kind = "module"
# Maximum weak and strong fairness conditions in a generated specification.
max_fairness = 2
ignore = ["unbound", "exotic"]
```

| Category | Enables |
| --- | --- |
| `action` | Post-assignment guards that read primed variables, the operand of `ENABLED`, and the actions inside `[A]_v`, `<<A>>_v`, `WF` and `SF`. |
| `temporal` | The property section and `ENABLED`: `[]`, `<>`, `~>`, `[][A]_v`, `<><<A>>_v`, `WF` and `SF`. `ENABLED` may also occur in `Init`, `Inv`, definitions and guards. |
| `exotic` | `-+->`, `\EE`, `\AA` and `\cdot`. Keep it ignored: specifications rarely use these forms, and TLC and Apalache both crash on them. No signature quarantines them. |

Enabling `action` without `temporal` adds post-assignment guards but no
property. `max_fairness = 0` generates properties without fairness conditions in
`Spec`; `WF` and `SF` can still occur inside the property formula. A corpus that
ignores `temporal` keeps the byte layout it had before these operators existed.

In an `expr` corpus, which declares no state variables, these categories
enable only `ENABLED` of constant actions. Every other action or temporal form
has nothing to apply to.

## 2. What a generated module contains

```tla
Next == \/ step < 5 /\ guards /\ assignments /\ step' = step + 1 /\ postGuards
        \/ ...
        \/ UNCHANGED <<var0, var1, step>>
Inv == ...
Fairness == WF_<<var0>>(var0' > var0)                          \* TRUE if none
Spec == Init /\ [][Next]_<<var0, var1, step>> /\ Fairness
Prop == [][var1' # var1]_var1 /\ \A q \in {1, 2} : (<>(var0 = q) ~> [](var1 = 0))
Liveness == Fairness => Prop
```

- **Post-assignment guards** follow `step' = step + 1`. Every primed variable
  they mention has already been assigned.
- **The step guard and the stuttering disjunct** replace the former `Bound`
  state constraint. No state beyond `step = max_steps` is reachable and every
  state may stutter, so both checkers see the same finite graph and the same
  infinite behaviors.
- **The property** is optional. It is one temporal formula, nested as TLA<sup>+</sup>
  allows: through the Boolean connectives including `<=>`, `IF` branches, `CASE`
  arms, `LET` bodies, quantifier bodies, `[]`, `<>` and `~>`. An action appears
  only inside `[][A]_v`, `<><<A>>_v`, `WF` or `SF`.

`fuzztla print` renders the whole module, including `Spec`, `Prop` and
`Liveness`. The printer parenthesizes `[]([Next]_(vars))`; SANY, TLC and
Apalache treat it as `[][Next]_vars`.

## 3. What each checker is asked

| | Without a property | With a property |
| --- | --- | --- |
| TLC configuration | `SPECIFICATION Spec`, `INVARIANT Inv` | adds `PROPERTY Prop` |
| Apalache arguments | `--init=Init --next=Next --inv=Inv --length=max_steps` | adds `--temporal=Liveness`, with `--length=max_steps + 1` |

TLC applies fairness through `Spec`. Apalache does not support fairness, so it
is asked to check `Liveness`, which states the same obligation as an
implication.

## 4. Expected results

| Module contains | TLC | Apalache | Aggregation |
| --- | --- | --- | --- |
| No `ENABLED`, `WF` or `SF` | `pass` or `counterexample` | the same verdict | compared as usual |
| `ENABLED` anywhere | evaluates it | `fail`, "unsupported expression: ENABLED" | fails; the triager classifies it as a known limitation |
| `WF` or `SF` | evaluates it | `fail` (`spec_eval`), "Handling fairness is not supported yet!" | fails; the triager classifies it as a known limitation |
| A property that is constant `FALSE` or a tautology | `fail`, "The property of Prop is equal to FALSE" or "Temporal formula is a tautology" | checks it | fails; the triager classifies it as a TLC restriction |

A property that makes the parser stage fail with a level error is a decoder
defect, not a SANY finding. Report it against the generator.

## 5. Shapes TLC cannot check

TLC cannot check some well-formed temporal formulas: a temporal formula under
`<=>`, in a `CASE` arm or under an unbounded quantifier, and `<><<A>>_v` or a
`[][A]_v` nested under another temporal operator. The shipped known-defect
database quarantines modules with these shapes, so they land in
`00-known-defects` with the signature id instead of crashing TLC. Keep
`known_defects` pointing at `signatures/known-defects.toml`; without it, expect
roughly 2% of generated modules to crash TLC. The
[conformance document](../../conformance/tlc-temporal-formula-limits.md) lists
each shape with a reproduction.

Checking a property adds one transition to Apalache's unrolling and doubles its
state variables. Expect Apalache time per module to rise when `temporal` is
enabled. In a 400-module smoke run, `ENABLED` and fairness accounted for 15 of 240
aggregator deviations, and a constant property for 10; ADR 0007 lists the full
counts.
