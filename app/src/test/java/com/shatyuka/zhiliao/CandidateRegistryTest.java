package com.shatyuka.zhiliao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CandidateRegistryTest {

    private static void load(String json) {
        assertTrue(CandidateRegistry.install(json));
    }

    @Test
    public void parseBuiltInCandidates() {
        load("{\"schemaVersion\":1,\"revision\":7,\"symbols\":{\"autoRefresh.returnTopOrRefresh\":[\"returnTopOrRefresh\",\"a\"]}}");
        assertEquals(7, CandidateRegistry.getRevision());
        assertArrayEquals(new String[]{"returnTopOrRefresh", "a"},
                CandidateRegistry.candidates("autoRefresh.returnTopOrRefresh"));
        assertNull(CandidateRegistry.candidates("not.configured"));
        CandidateRegistry.reset();
    }

    @Test
    public void badSchemaFailsClosed() {
        // 坏数据一律拒绝并保持空表：解析失败绝不能让 Hook 拿到脏名单
        String[] badJsons = {
                "{\"schemaVersion\":2,\"revision\":1,\"symbols\":{\"k\":[\"a\"]}}",
                "{\"schemaVersion\":1,\"revision\":0,\"symbols\":{\"k\":[\"a\"]}}",
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"1bad\":[\"a\"]}}",
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"k\":[]}}",
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"k\":[\"9bad\"]}}",
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"k\":[\"a b\"]}}",
                "not json at all",
        };
        for (String bad : badJsons) {
            boolean loaded = CandidateRegistry.install(bad);
            assertNull("应拒绝: " + bad, CandidateRegistry.candidates("k"));
            if (loaded) {
                CandidateRegistry.reset();
            }
        }
        CandidateRegistry.reset();
    }

    @Test
    public void resolverPrefersVerifiedCandidateOverFirstMatch() throws Exception {
        load("{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"t.method\":[\"a\",\"b\"]}}");
        // 两个同名候选都存在：名字排序 a 在前，但只有 b 通过验证器——
        // 旧 first-match 会误绑 a，按名单+验证必须命中 b
        Method hit = TargetResolver.findMethodByCandidates(BothNames.class, "t.method", false,
                method -> "b".equals(method.getName()));
        assertNotNull(hit);
        assertEquals("b", hit.getName());
        CandidateRegistry.reset();
    }

    @Test
    public void resolverReturnsNullWhenNoCandidateVerifies() throws Exception {
        load("{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"t.method\":[\"a\"]}}");
        assertNull(TargetResolver.findMethodByCandidates(BothNames.class, "t.method", false,
                method -> false));
        assertNull(TargetResolver.findMethodByCandidates(BothNames.class, "missing.key", false,
                method -> true));
        CandidateRegistry.reset();
    }

    static class BothNames {
        public void a() {
        }

        public void b() {
        }
    }

    @Test
    public void qualifiedClassCandidatesResolveByVerifier() throws Exception {
        load("{\"schemaVersion\":1,\"revision\":2,\"symbols\":{\"t.class\":[\"java.lang.String\",\"java.util.ArrayList\"]}}");
        Class<?> hit = TargetResolver.findClassByQualifiedCandidates(
                getClass().getClassLoader(), "t.class", clazz -> clazz == java.util.ArrayList.class);
        assertEquals(java.util.ArrayList.class, hit);
        assertNull(TargetResolver.findClassByQualifiedCandidates(
                getClass().getClassLoader(), "t.class", clazz -> false));
        assertNull(TargetResolver.findClassByQualifiedCandidates(
                getClass().getClassLoader(), "missing.key", clazz -> true));
        CandidateRegistry.reset();
    }

    @Test
    public void collectsEveryQualifiedClassThatVerifies() {
        // 有些目标是一串同构实现（多个 Retrofit 转换器类）都要挂，缺一个就是功能不全
        load("{\"schemaVersion\":1,\"revision\":4,\"symbols\":{\"t.classes\":[\"java.lang.String\",\"java.util.ArrayList\",\"no.such.Clazz\"]}}");

        java.util.List<Class<?>> hits = TargetResolver.findClassesByQualifiedCandidates(
                getClass().getClassLoader(), "t.classes", clazz -> true);

        assertEquals(2, hits.size());
        assertEquals(java.lang.String.class, hits.get(0));
        assertEquals(java.util.ArrayList.class, hits.get(1));
        CandidateRegistry.reset();
    }

    @Test
    public void dottedCandidatesKeepMethodLookupWorking() throws Exception {
        // 同一个名单里既有类全名也有方法名：解析方法时不能因为多了带点号的候选就乱掉
        load("{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"t.method\":[\"a\",\"com.x.Y\"],\"t.class\":[\"java.lang.String\"]}}");
        assertArrayEquals(new String[]{"java.lang.String"}, CandidateRegistry.candidates("t.class"));
        Method hit = TargetResolver.findMethodByCandidates(BothNames.class, "t.method", false, method -> true);
        assertNotNull(hit);
        assertEquals("a", hit.getName());
        CandidateRegistry.reset();
    }

    @Test
    public void fieldCandidatesAreVerifiedByType() throws Exception {
        // 字段名和类名一样会变（H5Event 里那个参数表就叫过 i / j / k），只认名字不够，类型必须对得上
        load("{\"schemaVersion\":1,\"revision\":4,\"symbols\":{\"t.field\":[\"missing\",\"notIt\",\"ok\"]}}");

        java.lang.reflect.Field hit = TargetResolver.findFieldByCandidates(FieldHolder.class, "t.field",
                field -> field.getType() == java.util.Map.class);

        assertNotNull(hit);
        assertEquals("ok", hit.getName());
        CandidateRegistry.reset();
    }

    static class FieldHolder {
        String notIt;
        int another;
        java.util.Map<String, String> ok;
    }
}
