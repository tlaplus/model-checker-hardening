package io.github.tlaplus.hardening.cli;

import java.util.Optional;

/**
 * Acquires a live progress display for one run. Only the process entry point acquires the physical
 * terminal; commands constructed by embedded callers use {@link #NONE} and report plainly to their
 * configured writer.
 */
@FunctionalInterface
interface TerminalAcquisition {
    TerminalAcquisition NONE = feedback -> Optional.empty();

    Optional<TerminalProgressDisplay> open(boolean feedback);
}
