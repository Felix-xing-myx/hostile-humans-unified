package dev.felix.hostilehumans.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches optional API accessors, not their returned values; absent members are cached too. */
public final class CachedReflection {
    private static final class Accessors {
        final Map<String, Optional<Method>> methods = new ConcurrentHashMap<>();
        final Map<String, Optional<Field>> fields = new ConcurrentHashMap<>();
    }
    private static final ClassValue<Accessors> CACHE = new ClassValue<>() {
        @Override protected Accessors computeValue(Class<?> type) { return new Accessors(); }
    };
    private CachedReflection() { }

    public static Object call(Object instance, String name) {
        if (instance == null) return null;
        Class<?> type = instance.getClass();
        Optional<Method> method = CACHE.get(type).methods.computeIfAbsent(name, key -> {
            try { return Optional.of(type.getMethod(key)); }
            catch (ReflectiveOperationException | LinkageError error) { return Optional.empty(); }
        });
        if (method.isEmpty()) return null;
        try { return method.get().invoke(instance); }
        catch (ReflectiveOperationException | LinkageError error) { return null; }
    }

    public static Object field(Object instance, String name) {
        if (instance == null) return null;
        Class<?> type = instance.getClass();
        Optional<Field> field = CACHE.get(type).fields.computeIfAbsent(name, key -> {
            try { return Optional.of(type.getField(key)); }
            catch (ReflectiveOperationException | LinkageError error) { return Optional.empty(); }
        });
        if (field.isEmpty()) return null;
        try { return field.get().get(instance); }
        catch (ReflectiveOperationException | LinkageError error) { return null; }
    }

    static int methodEntries(Class<?> type) { return CACHE.get(type).methods.size(); }
    static int fieldEntries(Class<?> type) { return CACHE.get(type).fields.size(); }
}
