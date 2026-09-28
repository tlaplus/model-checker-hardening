package io.github.tlaplus.hardening.gen.rewrite;

import io.github.tlaplus.hardening.common.Preconditions;

/**
 * Bounds of one rewrite (ADR 0016 §2, §7).
 *
 * @param maximumRewrites the rewrites applied to one rewritable body
 * @param maximumRewriteDepth the rewrites stacked on one node
 */
public record RewriteLimits(int maximumRewrites, int maximumRewriteDepth) {
    public RewriteLimits {
        Preconditions.requireNonnegative(maximumRewrites, "maximumRewrites");
        Preconditions.requirePositive(maximumRewriteDepth, "maximumRewriteDepth");
    }

    public static RewriteLimits defaults() {
        return new RewriteLimits(16, 4);
    }
}
