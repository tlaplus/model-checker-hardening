package io.github.tlaplus.hardening.cli;

import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Characters for the live diagram and change markers. Every glyph occupies one column, so both sets
 * share the same layout geometry.
 */
enum Glyphs {
    ASCII(" |||-+++-+++-+++", "v", ">", "`", "+", "-"),
    UNICODE(" \u2575\u2577\u2502\u2574\u2518\u2510\u2524\u2576\u2514\u250c\u251c\u2500\u2534\u252c\u253c",
            "\u25bc", "\u25b6", "\u2514", "\u2191", "\u2193");

    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 4;
    private static final int RIGHT = 8;

    /** Indexed by the bit set of connected directions. */
    private final String junctions;
    private final String arrowDown;
    private final String arrowHead;
    private final String corner;
    private final String rise;
    private final String fall;

    Glyphs(String junctions, String arrowDown, String arrowHead, String corner, String rise, String fall) {
        this.junctions = junctions;
        this.arrowDown = arrowDown;
        this.arrowHead = arrowHead;
        this.corner = corner;
        this.rise = rise;
        this.fall = fall;
    }

    /** The richest set the charset can encode. */
    static Glyphs detect(Charset charset) {
        return Optional.ofNullable(charset)
                .filter(encoding -> encoding.newEncoder().canEncode(UNICODE.all()))
                .map(_ -> UNICODE)
                .orElse(ASCII);
    }

    /** A line segment or junction connecting the given neighbors. */
    String junction(boolean up, boolean down, boolean left, boolean right) {
        var index = (up ? UP : 0) | (down ? DOWN : 0) | (left ? LEFT : 0) | (right ? RIGHT : 0);
        return junctions.substring(index, index + 1);
    }

    String vertical() {
        return junction(true, true, false, false);
    }

    String arrowDown() {
        return arrowDown;
    }

    /** A rightward arrow of the given width, at least one column. */
    String arrowRight(int width) {
        return junction(false, false, true, true).repeat(Math.max(0, width - 1)) + arrowHead;
    }

    /** The corner where a vertical line from above turns right. */
    String corner() {
        return corner;
    }

    /** One column; blank for a change without an order. */
    String marker(RunValue.Direction direction) {
        return switch (direction) {
            case UP -> rise;
            case DOWN -> fall;
            case CHANGED -> " ";
        };
    }

    String all() {
        return String.join("", junctions, arrowDown, arrowHead, corner, rise, fall);
    }
}
