# Actions, temporal properties and fairness

> **Status:** Implemented.
> [ADR 0007](../decisions/0007-levels-and-temporal-properties.md) records the
> design and the checker measurements behind it.

A `module` corpus can generate primed guards in `Next`, a temporal property
checked by both model checkers, and fairness conditions in the specification.
Generation respects TLA<sup>+</sup> levels, so SANY accepts every such module.
The generated properties use only the forms that both TLC and Apalache accept.

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
| `temporal` | The property section and `ENABLED`: `[]`, `<>`, `~>`, `[]<><<A>>_v`, `<>[][A]_v`, top-level `[][A]_v`, `WF` and `SF`. `ENABLED` may also occur in `Init`, `Inv`, definitions and guards. |
| `exotic` | `-+->`, `\EE`, `\AA` and `\cdot`. Keep it ignored: TLC and Apalache both crash on these forms. |

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
Prop == [][var1' # var1]_var1 /\ (<>(var0 = 2) => []<><<var1' = 0>>_var1)
Liveness == Fairness => Prop
```

- **Post-assignment guards** follow `step' = step + 1`. Every primed variable
  they mention has already been assigned.
- **The step guard and the stuttering disjunct** replace the former `Bound`
  state constraint. No state beyond `step = max_steps` is reachable and every
  state may stutter, so both checkers see the same finite graph and the same
  infinite behaviors.
- **The property** is optional. It is a conjunction of top-level `[][A]_v`
  conjuncts and one temporal formula. Inside that formula, an action appears
  only as `[]<><<A>>_v`, `<>[][A]_v`, `WF` or `SF`, and only below `~`, `/\`,
  `\/`, `=>`, `IF` branches and `LET` bodies. A temporal formula never occurs
  under a quantifier, `<=>` or `CASE`. These are the nestings TLC and Apalache
  both check; the others crash one of them. TLC's limitation for `<=>` is
  [tlaplus/tlaplus#1029](https://github.com/tlaplus/tlaplus/issues/1029).

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
| `ENABLED` anywhere | evaluates it | `fail`, "unsupported expression: ENABLED" | fails |
| `WF` or `SF` | evaluates it | `fail` (`spec_eval`), "Handling fairness is not supported yet!" | fails |

A property that makes the parser stage fail with a level error is a decoder
defect, not a SANY finding. Report it against the generator.

Checking a property adds one transition to Apalache's unrolling and doubles its
state variables. Expect Apalache time per module to rise when `temporal` is
enabled.
