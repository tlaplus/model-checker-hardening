package io.github.tlaplus.hardening.gen.rewrite;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Bounds of one rewrite (ADR 0016 §2, §7).
 *
 * @param maximumRewrites the rewrites applied to one rewritable body
 * @param maximumRewriteDepth the rewrites stacked on one node
 * @param maximumGrowth how many times its original size a rewritten body may reach, beside the
 *     node budget of one fresh operand; a rule that duplicates a parameter, applied repeatedly,
 *     would otherwise double the body with every rewrite
 */
public record RewriteLimits(int maximumRewrites, int maximumRewriteDepth, int maximumGrowth) {
    public RewriteLimits {
        Preconditions.requireNonnegative(maximumRewrites, "maximumRewrites");
        Preconditions.requirePositive(maximumRewriteDepth, "maximumRewriteDepth");
        Preconditions.requirePositive(maximumGrowth, "maximumGrowth");
    }

    public static RewriteLimits defaults() {
        return new RewriteLimits(16, 4, 2);
    }
}
