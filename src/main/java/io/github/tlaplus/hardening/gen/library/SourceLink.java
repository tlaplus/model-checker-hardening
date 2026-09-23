package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.TlaModule;
import java.util.List;
import java.util.Objects;

/**
 * The module the parser and TLC evaluate, and the aliases its renderer must define through named
 * instances. Without aliased (instance- or diff-linked) exports in use, the aliases are empty and
 * the module equals the self-contained one.
 */
public record SourceLink(TlaModule module, List<InstanceAlias> aliases) {
    public SourceLink {
        Objects.requireNonNull(module, "module");
        aliases = List.copyOf(aliases);
    }
}
