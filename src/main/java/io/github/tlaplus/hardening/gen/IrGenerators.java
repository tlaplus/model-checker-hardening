package io.github.tlaplus.hardening.gen;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.engine.IrGeneratorEngine;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaCheckedBuilder;

/**
 * Factory methods for deterministic generators of type-checked Apalache IR expressions.
 *
 * <p>Generation is type-directed. Each run first decodes a result type and then recursively chooses
 * expression forms that produce that type. Operands are generated with the types required by their
 * operators, and the resulting builder computation is finalized through {@link TlaCheckedBuilder}.
 * A successful run therefore returns an expression accepted by Apalache's checked Java builder,
 * rather than assembling unchecked IR nodes directly.
 *
 * <p>The result is one {@link TlaEx}, not a module or an operator declaration. Actual name
 * references are selected only from the active generation scope. The expression-only entry point
 * starts with an empty scope, and lexical constructs such as quantifiers, functions, lambdas, and
 * LET expressions introduce the bindings their bodies may reference. Model-value literals may
 * contain identifier-like text, but they are IR values rather than name references.
 *
 * <p>All choices come from the supplied {@link Draw}. A new internal builder, name supply, and
 * resource counter are created for every run, so a generator returned by this class can be reused
 * deterministically with independent cursors. Replaying the same generator configuration and byte
 * array produces structurally identical IR. The exact byte-to-expression mapping is
 * implementation-local: adding or reordering expression forms may change how later versions
 * interpret an existing byte array.
 *
 * <p>Variable-length structures do not decode a length field. Required elements are generated
 * first, and every optional element is introduced by a Boolean continuation marker: an odd byte
 * continues and an even byte terminates. The same protocol is used for tuple, record, variant,
 * argument, branch, update, set, and sequence collections, as well as string and arbitrary-precision
 * integer payloads. This makes mutations to element payload bytes less likely to shift every later
 * element merely by changing a decoded collection size.
 *
 * <p>Short input is expected. When bytes run out, recursive generation switches to a small terminal
 * expression of the requested type; an empty input deterministically produces {@code FALSE}.
 * Generation also switches to terminal expressions when configured depth or node budgets are
 * reached. These limits bound construction independently of the input length and prevent an endless
 * sequence of continuation markers from causing unbounded generation. The node budget counts
 * recursive expression-generation requests, not the exact number of final IR nodes or internal
 * {@code TlaCheckedBuilder} operations.
 *
 * <p>This class contains no random source, search procedure, retry loop, or shrinker. It is a
 * deterministic decoder intended to be driven by an external mutational fuzzer or another producer
 * of byte arrays.
 * Expected semantic dead ends, such as selecting a state-variable form when no state variable is
 * in scope, are reported as {@link InputRejectedException}; exhaustion and resource-budget fallback
 * are not rejections.
 *
 * @see Generator
 * @see Draw
 * @see IrGenerationConfig
 */
public final class IrGenerators {
    private IrGenerators() {}

    /**
     * Returns an expression generator using {@link IrGenerationConfig#defaults()}.
     *
     * <p>The returned generator is stateless: each call creates an independent checked builder and
     * name supply. It may therefore be reused, including concurrently when each invocation receives
     * a different {@link Draw}. The default limits are deliberately conservative for use with
     * arbitrary binary files and the checked builder's deferred computation model.
     *
     * @return reusable generator of checked TLA+ IR expressions
     */
    public static Generator<TlaEx> expressions() {
        return expressions(IrGenerationConfig.defaults());
    }

    /**
     * Returns an expression generator using the supplied resource limits.
     *
     * <p>The configuration is captured when this method is called. Every invocation of the returned
     * generator starts fresh counters but applies the same maximum type depth, expression depth,
     * expression-request count, collection size, string payload size, and integer payload size.
     * Increasing these limits permits larger and more deeply nested expressions, while also
     * increasing checked-builder work and stack usage.
     *
     * <p>Builder validation failures are propagated as runtime exceptions. Such a failure indicates
     * either a defect in an expression form implemented here or an incompatibility with the linked
     * Apalache version; malformed or exhausted input alone is handled by deterministic fallback
     * expressions and is not considered an error. A selected form that cannot satisfy a decoded
     * semantic constraint instead throws {@link InputRejectedException}; callers may skip that
     * input without suppressing other runtime failures.
     *
     * @param config immutable limits applied to every generation
     * @return reusable generator of checked TLA+ IR expressions
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public static Generator<TlaEx> expressions(IrGenerationConfig config) {
        Objects.requireNonNull(config, "config");
        return new IrGeneratorEngine(config)::generate;
    }
}
