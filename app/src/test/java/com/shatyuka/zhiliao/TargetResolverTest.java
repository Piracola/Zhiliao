package com.shatyuka.zhiliao;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;

public class TargetResolverTest {
    static class ShiftedMethods {
        public final void f0(boolean value) {
        }

        public void a0(boolean value) {
        }

        public void c0(CharSequence value) {
        }

        public void e0(CharSequence value) {
        }
    }

    @Test
    public void generatesBothR8NameStylesWithoutGaps() {
        assertEquals("a", TargetResolver.legacyNameAt(1));
        assertEquals("z", TargetResolver.legacyNameAt(26));
        assertEquals("aa", TargetResolver.legacyNameAt(27));
        assertEquals("a0", TargetResolver.compactNameAt(27));
        assertEquals("z0", TargetResolver.compactNameAt(52));
        assertEquals("a1", TargetResolver.compactNameAt(53));
    }

    @Test
    public void resolvesMethodsByStructureInsteadOfObfuscatedName() throws Exception {
        Method summary = TargetResolver.requireMethod(ShiftedMethods.class, false, 0,
                "setSummary", method -> method.getReturnType() == void.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == CharSequence.class);
        Method visible = TargetResolver.requireMethod(ShiftedMethods.class, false, 0,
                "setVisible", method -> Modifier.isFinal(method.getModifiers())
                        && method.getReturnType() == void.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == boolean.class);

        assertEquals("c0", summary.getName());
        assertEquals("f0", visible.getName());
    }
}
