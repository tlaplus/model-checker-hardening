package io.github.tlaplus.hardening.config;

/** Reports invalid contents in a FuzzTLA configuration file. */
public final class ConfigException extends Exception {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
