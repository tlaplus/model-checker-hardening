package io.github.tlaplus.hardening.architecture.model;

import io.github.tlaplus.hardening.common.Role;

/** The names under which the atlas's views show roles. */
public final class RoleLabels {
    /** The label of a type without {@code @ArchRole}. */
    public static final String UNTAGGED = "other";

    private RoleLabels() {}

    /** The role's name in lower case with spaces, or {@value #UNTAGGED} for null. */
    public static String of(Role role) {
        return role == null ? UNTAGGED : role.name().toLowerCase().replace('_', ' ');
    }
}
