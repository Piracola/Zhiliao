package com.shatyuka.zhiliao.xposed;

/**
 * Replaces the hooked method instead of wrapping it: {@link #replaceHookedMethod} runs before,
 * and its return value becomes the result, so the original method is never invoked.
 */
public abstract class XC_MethodReplacement extends XC_MethodHook {
    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(replaceHookedMethod(param));
    }

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return result;
            }
        };
    }
}
