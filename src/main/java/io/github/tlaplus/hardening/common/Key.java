package io.github.tlaplus.hardening.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a top-level type as one of the few that carry its package, so that the architecture atlas
 * draws it in the package views (ADR 0022).
 *
 * <p>Mark a package's facade, its processes, and the model types that cross its boundary, but not
 * its helpers.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Key {}
