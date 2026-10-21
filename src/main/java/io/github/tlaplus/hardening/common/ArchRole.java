package io.github.tlaplus.hardening.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Role} of a top-level type for the architecture atlas (ADR 0022).
 *
 * <p>Source retention keeps the annotation out of the bytecode, so it adds no dependency on this
 * package that {@code jdeps} would report.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ArchRole {
    Role value();
}
