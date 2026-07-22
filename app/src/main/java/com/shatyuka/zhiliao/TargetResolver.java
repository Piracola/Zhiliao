package com.shatyuka.zhiliao;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TargetResolver {
    public interface ClassMatcher {
        boolean matches(Class<?> clazz) throws Exception;
    }

    public interface MethodMatcher {
        boolean matches(Method method);
    }

    private TargetResolver() {
    }

    public static Class<?> findObfuscatedClass(ClassLoader classLoader, String prefix,
                                                int cycleStart, int cycleRound,
                                                ClassMatcher matcher) {
        int start = Math.max(0, cycleStart * 26);
        int end = Math.max(start, (cycleStart + cycleRound) * 26);
        for (int i = start; i < end; i++) {
            Set<String> names = new LinkedHashSet<>();
            names.add(prefix + legacyNameAt(i));
            if (i > 26) {
                names.add(prefix + compactNameAt(i));
            }
            for (String name : names) {
                try {
                    Class<?> clazz = classLoader.loadClass(name);
                    if (matcher.matches(clazz)) {
                        return clazz;
                    }
                } catch (ClassNotFoundException | LinkageError ignored) {
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static Method requireMethod(Class<?> clazz, boolean includeInherited,
                                       int skip, String description,
                                       MethodMatcher matcher) throws NoSuchMethodException {
        Method method = findMethod(clazz, includeInherited, skip, matcher);
        if (method == null) {
            throw new NoSuchMethodException(clazz.getName() + "." + description);
        }
        return method;
    }

    public static Method findMethod(Class<?> clazz, boolean includeInherited,
                                    int skip, MethodMatcher matcher) {
        if (clazz == null) {
            return null;
        }
        Method[] methods = includeInherited ? clazz.getMethods() : clazz.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName)
                .thenComparing(method -> Arrays.toString(method.getParameterTypes())));
        int matched = 0;
        for (Method method : methods) {
            if (!matcher.matches(method)) {
                continue;
            }
            if (matched++ < skip) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    static String legacyNameAt(int index) {
        if (index <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int value = index;
        while (value > 0) {
            value--;
            result.insert(0, (char) ('a' + value % 26));
            value /= 26;
        }
        return result.toString();
    }

    static String compactNameAt(int index) {
        if (index <= 0) {
            return "";
        }
        int value = index - 1;
        char letter = (char) ('a' + value % 26);
        int round = value / 26;
        return round == 0 ? String.valueOf(letter) : letter + String.valueOf(round - 1);
    }
}
