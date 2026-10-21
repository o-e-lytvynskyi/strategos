package dev.strategos.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.function.Predicate;

/** The predicates and conditions of the mod rules; core's CoreArchitectureTest has its own copies. */
final class ArchPredicates {
    private static final List<String> BOXED = List.of(
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Boolean");

    /** The core packages mod code may use whole; elsewhere only {@code @CoreApi} types. */
    private static final List<String> CORE_API_PACKAGES = List.of(
            "dev.strategos.core.shared",
            "dev.strategos.core.protocol",
            "dev.strategos.core.content",
            "dev.strategos.core.port");

    private static final String CORE_API = "dev.strategos.core.shared.CoreApi";

    private ArchPredicates() {}

    /** A call to one of {@code names} declared by {@code owner}. */
    static DescribedPredicate<JavaMethodCall> callTo(String owner, String... names) {
        List<String> banned = List.of(names);
        return DescribedPredicate.describe(
                "a call to " + owner + "." + String.join("/", names),
                call -> call.getTargetOwner().getName().equals(owner) && banned.contains(call.getName()));
    }

    static DescribedPredicate<JavaMethodCall> callToAny(List<String> owners, Predicate<String> name) {
        return DescribedPredicate.describe(
                "a banned call on " + String.join(", ", owners),
                call -> owners.contains(call.getTargetOwner().getName()) && name.test(call.getName()));
    }

    /** Boxing ({@code valueOf}) or unboxing ({@code intValue()} and the like) on a wrapper type. */
    static DescribedPredicate<JavaMethodCall> boxingCall() {
        return DescribedPredicate.describe(
                "boxing or unboxing",
                call -> BOXED.contains(call.getTargetOwner().getName())
                        && (call.getName().equals("valueOf") || call.getName().endsWith("Value")));
    }

    /**
     * B2: every dependency on core is on {@code core.shared/protocol/content/port}, or on a type that (or whose
     * enclosing type) is {@code @CoreApi}; {@code mod.client} may also use {@code core.client}.
     */
    static ArchCondition<JavaClass> onlyUseTheCoreApi() {
        return new ArchCondition<>("only use the core's API") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                boolean client = isIn(origin.getPackageName(), "dev.strategos.mod.client");
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String pkg = target.getPackageName();
                    if (!isIn(pkg, "dev.strategos.core")
                            || CORE_API_PACKAGES.stream().anyMatch(api -> isIn(pkg, api))
                            || (client && isIn(pkg, "dev.strategos.core.client"))
                            || isCoreApi(target)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            dependency, dependency.getDescription() + ", which is not core API"));
                }
            }
        };
    }

    private static boolean isCoreApi(JavaClass type) {
        if (type.isAnnotatedWith(CORE_API)) {
            return true;
        }
        return type.getEnclosingClass().map(ArchPredicates::isCoreApi).orElse(false);
    }

    private static boolean isIn(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }

    static ArchCondition<JavaField> beFinalAndImmutablyTyped() {
        return new ArchCondition<>("be final and of no array, collection, map, atomic or fastutil type") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaClass type = field.getRawType();
                String problem = null;
                if (!field.getModifiers().contains(JavaModifier.FINAL)) {
                    problem = "is not final";
                } else if (type.isArray()) {
                    problem = "is an array";
                } else if (type.isAssignableTo(java.util.Collection.class)
                        || type.isAssignableTo(java.util.Map.class)) {
                    problem = "is a " + type.getSimpleName();
                } else if (isIn(type.getPackageName(), "java.util.concurrent.atomic")
                        || isIn(type.getPackageName(), "it.unimi.dsi.fastutil")) {
                    problem = "is a mutable " + type.getName();
                }
                if (problem != null) {
                    events.add(SimpleConditionEvent.violated(
                            field, "static field " + field.getFullName() + " " + problem));
                }
            }
        };
    }

    static ArchCondition<JavaClass> resideInANullMarkedPackage() {
        return new ArchCondition<>("reside in a @NullMarked package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!javaClass.getPackage().isAnnotatedWith("org.jspecify.annotations.NullMarked")) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " resides in " + javaClass.getPackageName()
                                    + ", which has no @NullMarked package-info"));
                }
            }
        };
    }
}
