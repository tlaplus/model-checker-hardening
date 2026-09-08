package io.github.tlaplus.hardening.common;

import java.util.List;
import java.util.Map;
import scala.jdk.javaapi.CollectionConverters;

/** Java/Scala collection boundaries used by the IR APIs. */
public final class ScalaCollections {
    private ScalaCollections() {}

    public static <T> List<T> list(scala.collection.Seq<T> values) {
        return List.copyOf(CollectionConverters.asJava(values));
    }

    public static <T> scala.collection.immutable.Seq<T> seq(List<T> values) {
        return CollectionConverters.asScala(values).toSeq();
    }

    public static <K, V> Map<K, V> map(scala.collection.Map<K, V> values) {
        return CollectionConverters.asJava(values);
    }
}
