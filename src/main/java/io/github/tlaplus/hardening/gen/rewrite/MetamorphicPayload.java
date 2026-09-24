package io.github.tlaplus.hardening.gen.rewrite;

import io.github.tlaplus.hardening.gen.Draw;
import java.util.Objects;

/**
 * The layout of a metamorphic input (ADR 0016 §1):
 *
 * <pre>[base length: 2 bytes, big-endian] [base payload] [rewrite payload]</pre>
 *
 * <p>The base payload is byte-identical to a stored conformance entry, so an adopted parent
 * decodes to the same module, and byte edits of the rewrite payload never move the base. A length
 * beyond the input is clamped.
 */
public final class MetamorphicPayload {
    /** The largest base payload the two-byte header can describe. */
    public static final int MAXIMUM_BASE_BYTES = 0xFFFF;

    private MetamorphicPayload() {}

    /** The two parts of a decoded payload, each an independent cursor. */
    public record Parts(Draw base, Draw rewrite) {}

    /** Splits {@code draw}; the rewrite part is everything after the base part. */
    public static Parts split(Draw draw) {
        Objects.requireNonNull(draw, "draw");
        var length = (draw.drawByte() << 8) | draw.drawByte();
        var base = draw.slice(length);
        return new Parts(base, draw.slice(draw.remaining()));
    }

    /** Returns the offset of the rewrite payload in {@code input}: after the header and the base. */
    public static int rewriteOffset(byte[] input) {
        return input.length - split(new Draw(Objects.requireNonNull(input, "input"))).rewrite().remaining();
    }

    /** Encodes a base payload and a rewrite payload as one input. */
    public static byte[] encode(byte[] base, byte[] rewrite) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(rewrite, "rewrite");
        if (base.length > MAXIMUM_BASE_BYTES) {
            throw new IllegalArgumentException("a base payload holds at most " + MAXIMUM_BASE_BYTES + " bytes");
        }
        var input = new byte[2 + base.length + rewrite.length];
        input[0] = (byte) (base.length >>> 8);
        input[1] = (byte) base.length;
        System.arraycopy(base, 0, input, 2, base.length);
        System.arraycopy(rewrite, 0, input, 2 + base.length, rewrite.length);
        return input;
    }
}
