package io.github.tlaplus.hardening;

import io.github.tlaplus.hardening.cli.FuzzTlaCommand;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;

/**
 * The process entry point of {@code fuzztla}: runs {@link FuzzTlaCommand} and exits with its
 * status.
 */
@ArchRole(Role.ENTRY_POINT)
@Key
class Main {
    void main(String[] args) {
        System.exit(FuzzTlaCommand.execute(args));
    }
}
