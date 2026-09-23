package com.shatyuka.zhiliao.xposed;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;

/**
 * Legacy style hook callback, bridged onto the libxposed API 102 interceptor chain by {@link XposedBridge}.
 * The before/after callback model is kept so the hook implementations stay free of chain plumbing.
 */
public abstract class XC_MethodHook {
    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        MethodHookParam(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null)
                throw throwable;
            return result;
        }

        boolean isReturnEarly() {
            return returnEarly;
        }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class Unhook {
        private final Member hookedMethod;
        private final XposedInterface.HookHandle handle;

        Unhook(Member hookedMethod, XposedInterface.HookHandle handle) {
            this.hookedMethod = hookedMethod;
            this.handle = handle;
        }

        public Member getHookedMethod() {
            return hookedMethod;
        }

        public void unhook() {
            handle.unhook();
        }
    }
}
