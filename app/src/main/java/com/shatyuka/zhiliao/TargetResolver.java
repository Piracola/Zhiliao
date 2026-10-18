package com.shatyuka.zhiliao;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TargetResolver {
    public interface ClassMatcher {
        boolean matches(Class<?> clazz) throws Exception;
    }

    public interface FieldMatcher {
        boolean matches(Field field);
    }

    public interface MethodMatcher {
        boolean matches(Method method);
    }

    private TargetResolver() {
    }

    /**
     * 按候选名单解析方法名：依序尝试每个候选，必须同时通过签名验证器才算命中；
     * 全部落空返回 null（调用方再走结构化解析）。名单来自 CandidateRegistry。
     *
     * 与"第一个同名方法就用"的旧习惯不同，这里名字只是入场券，验证才是准绳——
     * 渠道包把混淆名挪给别人用时不会误绑。
     */
    public static Method findMethodByCandidates(Class<?> clazz, String registryKey,
                                                boolean includeInherited, MethodMatcher verifier) {
        String[] candidates = CandidateRegistry.candidates(registryKey);
        if (candidates == null || clazz == null) {
            return null;
        }
        Method[] methods = includeInherited ? clazz.getMethods() : clazz.getDeclaredMethods();
        for (String candidate : ResolutionCache.prioritize(registryKey, candidates)) {
            for (Method method : methods) {
                if (candidate.equals(method.getName()) && verifier.matches(method)) {
                    method.setAccessible(true);
                    ResolutionCache.remember(registryKey, candidate);
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 按候选名单收集**所有**通过的类：有些目标不是"选一个"，而是一串同构实现都要挂
     * （比如好几个版本的 Retrofit 转换器类同时存在于包里）。
     */
    public static java.util.List<Class<?>> findClassesByQualifiedCandidates(ClassLoader classLoader,
                                                                           String registryKey,
                                                                           ClassMatcher verifier) {
        java.util.List<Class<?>> result = new java.util.ArrayList<>();
        String[] candidates = CandidateRegistry.candidates(registryKey);
        if (candidates == null || classLoader == null) {
            return result;
        }
        for (String candidate : ResolutionCache.prioritize(registryKey, candidates)) {
            if (candidate.indexOf('.') < 0) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(candidate, false, classLoader);
                if (verifier.matches(clazz)) {
                    result.add(clazz);
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /** 按候选名单解析类名（全限定名的最后一段）。 */
    public static Class<?> findClassByCandidates(ClassLoader classLoader, String packageName,
                                                 String registryKey, ClassMatcher verifier) {
        String[] candidates = CandidateRegistry.candidates(registryKey);
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            try {
                Class<?> clazz = classLoader.loadClass(packageName + "." + candidate);
                if (verifier.matches(clazz)) {
                    return clazz;
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 按候选名单解析全限定类名：混淆后同一个类在不同渠道包里会落在不同包，
     * 只有全名能表达这种差异。名单里不带点号的候选在这里直接跳过。
     */
    public static Class<?> findClassByQualifiedCandidates(ClassLoader classLoader, String registryKey,
                                                          ClassMatcher verifier) {
        String[] candidates = CandidateRegistry.candidates(registryKey);
        if (candidates == null || classLoader == null) {
            return null;
        }
        for (String candidate : ResolutionCache.prioritize(registryKey, candidates)) {
            if (candidate.indexOf('.') < 0) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(candidate, false, classLoader);
                if (verifier.matches(clazz)) {
                    ResolutionCache.remember(registryKey, candidate);
                    return clazz;
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 按名单找字段。字段名和类名一样随版本变（H5Event 里那个 params 就叫过 i / j / k），
     * 所以候选列表 + 类型验证，一个都不能少。
     */
    public static Field findFieldByCandidates(Class<?> clazz, String registryKey, FieldMatcher verifier) {
        String[] candidates = CandidateRegistry.candidates(registryKey);
        if (candidates == null || clazz == null) {
            return null;
        }
        for (String candidate : ResolutionCache.prioritize(registryKey, candidates)) {
            try {
                Field field = clazz.getDeclaredField(candidate);
                if (verifier.matches(field)) {
                    field.setAccessible(true);
                    ResolutionCache.remember(registryKey, candidate);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
        }
        return null;
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



