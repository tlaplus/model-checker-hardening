package io.github.tlaplus.hardening.architecture;

import io.github.tlaplus.hardening.common.ArchModule;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

/**
 * The class dependencies between FuzzTLA's own top-level types, as {@code jdeps} reads them from
 * compiled classes (ADR 0022).
 *
 * <p>Nested types are folded into their top-level type, and a type's references to itself are
 * dropped. Annotations with source retention leave no trace in the bytecode, so they add no edges.
 */
public final class DependencyScanner {
    /** One class dependency between two top-level types, by qualified name. */
    public record ClassEdge(String from, String to) implements Comparable<ClassEdge> {
        @Override
        public int compareTo(ClassEdge other) {
            var byFrom = from.compareTo(other.from);
            return byFrom != 0 ? byFrom : to.compareTo(other.to);
        }
    }

    private static final Pattern EDGE =
            Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+\\S+\\s*$");

    private DependencyScanner() {}

    /** The compiled main classes, located through a class that lives there. */
    public static Path mainClasses() {
        try {
            var location = ArchModule.class.getProtectionDomain().getCodeSource().getLocation();
            return Path.of(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the main classes", e);
        }
    }

    /** The edges between the top-level types in {@code classes}, sorted and without duplicates. */
    public static List<ClassEdge> scan(Path classes) {
        var jdeps = ToolProvider.findFirst("jdeps")
                .orElseThrow(() -> new IllegalStateException("this JDK has no jdeps tool"));
        var out = new StringWriter();
        var err = new StringWriter();
        var regex = Pattern.quote(ArchModule.ROOT_PACKAGE) + "\\..*";
        var status = jdeps.run(new PrintWriter(out), new PrintWriter(err),
                "-verbose:class", "-filter:none", "-e", regex, classes.toString());
        if (status != 0) {
            throw new IllegalStateException("jdeps failed with status " + status + ": " + err);
        }
        var edges = new TreeSet<ClassEdge>();
        for (var line : out.toString().lines().toList()) {
            var matcher = EDGE.matcher(line);
            if (matcher.matches()) {
                var from = topLevel(matcher.group(1));
                var to = topLevel(matcher.group(2));
                if (!from.equals(to)) {
                    edges.add(new ClassEdge(from, to));
                }
            }
        }
        return new ArrayList<>(edges);
    }

    /** The package of a qualified type name. */
    public static String packageOf(String qualifiedName) {
        var dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? "" : qualifiedName.substring(0, dot);
    }

    private static String topLevel(String binaryName) {
        var dollar = binaryName.indexOf('$');
        return dollar < 0 ? binaryName : binaryName.substring(0, dollar);
    }
}
