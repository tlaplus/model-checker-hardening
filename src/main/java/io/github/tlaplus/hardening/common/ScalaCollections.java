package io.github.tlaplus.hardening.common;

import java.util.List;
import java.util.Map;
import scala.jdk.javaapi.CollectionConverters;

/** Java/Scala collection boundaries used by the IR APIs. */
public final class ScalaCollections {
    private ScalaCollections() {}

    /** An immutable copy, in the sequence's own order. */
    public static <T> List<T> list(scala.collection.Seq<T> values) {
        return List.copyOf(CollectionConverters.asJava(values));
    }

    public static <T> scala.collection.immutable.Seq<T> seq(List<T> values) {
        return CollectionConverters.asScala(values).toSeq();
    }

    /**
     * A view, not a copy. Apalache row types carry their fields in a sorted map, and that order is
     * the order library fields are inspected and drawn in, so it must not be discarded here.
     */
    public static <K, V> Map<K, V> map(scala.collection.Map<K, V> values) {
        return CollectionConverters.asJava(values);
    }
}
