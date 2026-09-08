package io.github.tlaplus.hardening.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 identities for corpus payloads and external replay dependencies. */
public final class Digests {
    private Digests() {}

    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    public static String digest(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    public static String digest(Path path) throws IOException {
        var digest = sha256();
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) >= 0;) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
