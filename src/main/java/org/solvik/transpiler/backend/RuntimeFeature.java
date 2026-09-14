package org.solvik.transpiler.backend;

import java.util.EnumSet;
import java.util.Set;

/**
 * The optional runtime facilities a generated program may need.
 *
 * <p>Reachability is tracked structurally while the Java IR is built: the
 * lowerer records a feature exactly when it chooses a runtime helper, a runtime
 * type spelling, or a runtime constructor. The emitter never scans rendered
 * source text to rediscover this.</p>
 *
 * <p>Some facilities depend on others (for example JSON serialization needs the
 * list and map runtime); {@link #closure()} folds those dependencies in
 * explicitly.</p>
 */
public enum RuntimeFeature {
    LIST,
    MAP,
    STACK,
    SET,
    MONITOR,
    REGEX,
    THREAD,
    MUTEX,
    SEMAPHORE,
    PROCESS,
    JSON,
    HASH,
    FILE,
    DYNAMIC,
    RANDOM,
    PROPERTIES,
    ENV,
    TYPE,
    CONVERSIONS,
    STRING_ITER,
    STRING_ACCESS,
    RANGE,
    ARITHMETIC,
    TIME,
    TEST;

    /** Facilities this feature transitively requires. */
    public Set<RuntimeFeature> requires() {
        return switch (this) {
            case JSON -> EnumSet.of(LIST, MAP);
            case PROCESS, ENV, FILE, RANGE, REGEX, MAP, SET -> EnumSet.of(LIST);
            case THREAD -> EnumSet.of(MONITOR);
            case CONVERSIONS -> EnumSet.of(STRING_ACCESS);
            default -> EnumSet.noneOf(RuntimeFeature.class);
        };
    }

    /** Expands a set to a fixed point of {@link #requires()}. */
    public static EnumSet<RuntimeFeature> closure(EnumSet<RuntimeFeature> features) {
        EnumSet<RuntimeFeature> result = features.isEmpty() ? EnumSet.noneOf(RuntimeFeature.class) : EnumSet.copyOf(features);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (RuntimeFeature feature : EnumSet.copyOf(result)) {
                for (RuntimeFeature dependency : feature.requires()) changed |= result.add(dependency);
            }
        }
        return result;
    }
}
