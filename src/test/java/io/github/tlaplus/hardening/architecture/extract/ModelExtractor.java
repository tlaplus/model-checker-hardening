package io.github.tlaplus.hardening.architecture.extract;

import com.sun.source.util.DocTrees;
import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeKind;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.common.ArchModule;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Reads the atlas's facts about one package or top-level type from the doclet's element model:
 * its annotations, Javadoc, source position, and the relations its declarations state.
 */
final class ModelExtractor {
    private final DocTrees trees;
    private final JavadocText text;
    private final Path root;

    /** {@code root} is the repository root that source paths are relative to. */
    ModelExtractor(DocTrees trees, Path root) {
        this.trees = trees;
        this.text = new JavadocText(trees);
        this.root = root.toAbsolutePath().normalize();
    }

    JavadocText text() {
        return text;
    }

    PackageNode packageNode(PackageElement element) {
        var name = element.getQualifiedName().toString();
        var module = ArchModule.ofPackage(name)
                .orElseThrow(() -> new IllegalStateException("no ArchModule declares " + name));
        return new PackageNode(name, module.displayName(), text.summary(element), text.adrs(element));
    }

    TypeNode typeNode(TypeElement type) {
        var role = type.getAnnotation(ArchRole.class);
        var path = trees.getPath(type);
        var unit = path.getCompilationUnit();
        var file = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
        var start = trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
        return new TypeNode(
                type.getQualifiedName().toString(),
                ((PackageElement) type.getEnclosingElement()).getQualifiedName().toString(),
                kind(type.getKind()),
                role == null ? null : role.value(),
                type.getAnnotation(Key.class) != null,
                text.summary(type),
                root.relativize(file).toString().replace(File.separatorChar, '/'),
                (int) unit.getLineMap().getLineNumber(start),
                text.adrs(type));
    }

    /**
     * The strongest relation from {@code top} to each type in {@code known} that its declarations
     * mention. A nested type's declarations count as {@link RelationKind#USES} of the top-level
     * type: they are not its supertypes or its state. Thrown exceptions are left to {@code jdeps},
     * so that they do not crowd the diagrams.
     */
    Map<String, RelationKind> relations(TypeElement top, Set<String> known) {
        var relations = new Relations(top.getQualifiedName().toString(), known);
        collect(top, true, relations);
        return relations.kinds;
    }

    private void collect(TypeElement type, boolean own, Relations out) {
        var superclass = type.getSuperclass();
        if (superclass instanceof DeclaredType) {
            out.add(superclass, own ? RelationKind.EXTENDS : RelationKind.USES);
        }
        var interfaceKind = type.getKind() == ElementKind.INTERFACE
                ? RelationKind.EXTENDS
                : RelationKind.IMPLEMENTS;
        for (var supertype : type.getInterfaces()) {
            out.add(supertype, own ? interfaceKind : RelationKind.USES);
        }
        for (var field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            var isState = own && !field.getModifiers().contains(Modifier.STATIC);
            out.add(field.asType(), isState ? RelationKind.HAS : RelationKind.USES);
        }
        for (var component : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
            out.add(component.asType(), own ? RelationKind.HAS : RelationKind.USES);
        }
        for (var element : type.getEnclosedElements()) {
            if (element instanceof ExecutableElement executable
                    && !executable.getModifiers().contains(Modifier.PRIVATE)) {
                out.add(executable.getReturnType(), RelationKind.USES);
                executable.getParameters().forEach(p -> out.add(p.asType(), RelationKind.USES));
            }
        }
        for (var nested : ElementFilter.typesIn(type.getEnclosedElements())) {
            collect(nested, false, out);
        }
    }

    private static TypeKind kind(ElementKind kind) {
        return switch (kind) {
            case INTERFACE -> TypeKind.INTERFACE;
            case ENUM -> TypeKind.ENUM;
            case RECORD -> TypeKind.RECORD;
            case ANNOTATION_TYPE -> TypeKind.ANNOTATION;
            default -> TypeKind.CLASS;
        };
    }

    /** The relations of one top-level type to the known types, merged by strength. */
    private static final class Relations {
        private final String self;
        private final Set<String> known;
        private final Map<String, RelationKind> kinds = new TreeMap<>();

        Relations(String self, Set<String> known) {
            this.self = self;
            this.known = known;
        }

        void add(TypeMirror type, RelationKind kind) {
            var mentioned = new HashSet<String>();
            TypeMentions.collect(type, mentioned);
            for (var name : mentioned) {
                if (!name.equals(self) && known.contains(name)) {
                    kinds.merge(name, kind, RelationKind::strongest);
                }
            }
        }
    }
}
