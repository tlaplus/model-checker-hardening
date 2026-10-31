package io.github.tlaplus.hardening.workflow;

/**
 * Whether a run may extend a corpus that an earlier run has written (ADR 0026). A fresh corpus is
 * run the same way in either mode.
 */
public enum RunMode {
    /** Runs only a fresh corpus, so the run is determined by the configuration. */
    FRESH,
    /** Also continues a corpus that is not fresh, and marks it as not reproducible. */
    CONTINUE
}
