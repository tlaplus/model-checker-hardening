---
state: closed
labels: [manyan]
---

# Manyan reads a freed string table when it interns an operator synonym

## Summary

Manyan crashes with a segmentation fault (exit status 139) while parsing some modules that
TLC accepts. The fault is in `op_info`, which looks up an operator token:

```c
const char *name = S(canonical(t->id));   /* S(id) is strs[id].s */
```

`canonical` maps a synonym to its canonical name, for example `<=` to `\leq`, and interns
that name. Interning can grow the string table `strs` with `xrealloc`. C leaves unspecified
whether `strs` in `strs[canonical(...)]` is read before or after the call. The built binary
reads it before, so when the call grows the table, the lookup reads the freed array.

The crash needs the canonical name to be interned for the first time exactly when the table
is full: at 1,024 strings, then 2,048, and so on. So the crash depends on how many distinct
names the module has.

Observed with Manyan `v0.17.0` (soasme/manyan
`a9ca4f38028f4e8b4cc3f016167d071b55e3abe0`, 2026-11-24), built with GCC 13.3.0
(`make manyan PGO_TOOLS=`) on aarch64. The reference is TLC `bc1b506` (tlaplus/tlaplus
`bc1b506bdcad80bb518bf3776a9161c34ff0fc4c`) on OpenJDK 25.0.5.1. Apalache is not
involved. FuzzTLA `390ea420`.

Fixed in [Manyan v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0), by
`416ca2a`.

## Reproduction

`gen.py` writes a module with `n` definitions, then a first `<=`:

```python
import sys
n = int(sys.argv[1])
print("---- MODULE Many ----")
print("EXTENDS Naturals")
print("VARIABLE x")
for i in range(n):
    print(f"D{i} == {i}")
print("Init == x = 0")
print("Next == UNCHANGED x")
print("P == D0 <= 1")
print("====")
```

`Many.cfg`:

```text
INIT Init
NEXT Next
```

```sh
python3 gen.py 972 > Many.tla
manyan -workers 1 -config Many.cfg Many.tla
```

Manyan exits 139 and prints nothing. With 971 or 973 definitions it exits 0. TLC exits 0
for all three. A build with `-g -O0` shows the realloc inside the lookup:

```text
Breakpoint, intern_n (s="\\leq", len=4) at manyan.c:335    /* capstrs 1024 -> 2048 */
#2  canonical (id=257) at manyan.c:2725
#3  op_info (t=..., fix=2) at manyan.c:2731
#4  parse_postfix () at manyan.c:3291
Program received signal SIGSEGV
#0  strcmp ()
#1  op_info (t=..., fix=2) at manyan.c:2734
```

The number of definitions that triggers the crash depends on how many strings Manyan has
interned before parsing the module. For example, it is different with `-lint`.

## Expected behavior

Manyan should parse the module. Calling `canonical` before reading `strs` would fix it,
for example:

```c
int id = canonical(t->id);
const char *name = S(id);
```

## Impact

In a 25,000-entry pbt campaign, Manyan crashed this way on four entries: `643bdbb3`,
`81cb27fd`, `88a3cb5f` and `8c94659b`. All four are generated modules of more than 3,000
lines, and all four fault in `op_info` on a `<=`. TLC failed with status 75 on all four, so
the crash hid TLC's evaluation error. `script/triager.py` classifies a Manyan exit 139 as
this finding.

## Resolution

Manyan fixed this in
[`416ca2a`](https://github.com/soasme/manyan/commit/416ca2a700d998ad65bba67cd02374f6097f1a3d),
"Sequence string table lookup after interning in S() and op_info()", released in
[v0.18.0](https://github.com/soasme/manyan/releases/tag/v0.18.0) (soasme/manyan
`5522fa6b11b9e35ae60d46a31d0d33ce77df8ff9`, 2026-11-26). It is the first commit between
`v0.17.0` and `v0.18.0` that passes the reproduction; each commit was built with `make
manyan PGO_TOOLS=`.

Rechecked on 2026-11-26 with `v0.18.0`, built by `make manyan-build`, against the exit
statuses that TLC gave above: Manyan exits 0 on the reproduction for every number of
definitions from 1 to 4,200, which crosses the string table's reallocations at 1,024,
2,048 and 4,096 entries. FuzzTLA `dc773e71`.
