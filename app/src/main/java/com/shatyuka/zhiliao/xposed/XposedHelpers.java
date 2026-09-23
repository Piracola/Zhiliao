package com.shatyuka.zhiliao.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The subset of the legacy reflection helpers this module needs, rebuilt on plain reflection.
 */
public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(className);
        }
    }

    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null)
            throw new IllegalArgumentException("clazz must not be null");
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                XposedBridge.makeAccessible(method);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + "(" + describe(parameterTypes) + ")");
    }

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callbackOf(parameterTypesAndCallback);
        Method method = findMethodExact(clazz, methodName, parameterTypesOf(parameterTypesAndCallback));
        XposedBridge.hookMethod(method, callback);
    }

    public static void findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        XC_MethodHook callback = callbackOf(parameterTypesAndCallback);
        Class<?>[] parameterTypes = parameterTypesOf(parameterTypesAndCallback);
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            try {
                Constructor<?> constructor = current.getDeclaredConstructor(parameterTypes);
                XposedBridge.makeAccessible(constructor);
                XposedBridge.hookMethod(constructor, callback);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "(" + describe(parameterTypes) + ")");
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return invoke(findMethodBestMatch(obj.getClass(), methodName, args), obj, args);
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return invoke(findMethodBestMatch(clazz, methodName, args), null, args);
    }

    private static XC_MethodHook callbackOf(Object[] parameterTypesAndCallback) {
        Object last = parameterTypesAndCallback.length == 0
                ? null : parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof XC_MethodHook))
            throw new IllegalArgumentException("no XC_MethodHook callback defined");
        return (XC_MethodHook) last;
    }

    private static Class<?>[] parameterTypesOf(Object[] parameterTypesAndCallback) {
        Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object type = parameterTypesAndCallback[i];
            if (type instanceof Class<?>)
                parameterTypes[i] = (Class<?>) type;
            else if (type instanceof String)
                throw new IllegalArgumentException("class names are not supported, use Class objects: " + type);
            else
                throw new IllegalArgumentException("invalid parameter type: " + type);
        }
        return parameterTypes;
    }

    private static Method findMethodBestMatch(Class<?> clazz, String methodName, Object[] args) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && isAssignable(method.getParameterTypes(), args)) {
                    XposedBridge.makeAccessible(method);
                    return method;
                }
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + "(" + describe(args) + ")");
    }

    private static boolean isAssignable(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length)
            return false;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null ? parameterTypes[i].isPrimitive() : !box(parameterTypes[i]).isInstance(args[i]))
                return false;
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive())
            return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    private static String describe(Class<?>[] types) {
        StringBuilder builder = new StringBuilder();
        for (Class<?> type : types) {
            if (builder.length() > 0)
                builder.append(", ");
            builder.append(type.getName());
        }
        return builder.toString();
    }

    private static String describe(Object[] args) {
        StringBuilder builder = new StringBuilder();
        for (Object arg : args) {
            if (builder.length() > 0)
                builder.append(", ");
            builder.append(arg == null ? "null" : arg.getClass().getName());
        }
        return builder.toString();
    }

    private static Object invoke(Method method, Object obj, Object... args) {
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof Error)
                throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }
}
