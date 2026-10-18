package com.shatyuka.zhiliao;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** 解析缓存只改候选顺序、不跳过验证；失效靠指纹，不靠清理逻辑。 */
public class ResolutionCacheTest {
    private MemStore store;

    @Before
    public void setUp() {
        store = new MemStore();
        ResolutionCache.reset();
        CandidateRegistry.reset();
    }

    @After
    public void tearDown() {
        ResolutionCache.reset();
        CandidateRegistry.reset();
    }

    @Test
    public void prioritizeMovesCachedCandidateToFront() {
        ResolutionCache.attach(store, "v1");
        ResolutionCache.remember("k", "b");

        assertArrayEquals(new String[]{"b", "a", "c"},
                ResolutionCache.prioritize("k", new String[]{"a", "b", "c"}));
    }

    @Test
    public void prioritizeKeepsOrderWhenNothingCached() {
        ResolutionCache.attach(store, "v1");
        String[] original = {"a", "b"};

        assertSame(original, ResolutionCache.prioritize("k", original));
    }

    @Test
    public void changedFingerprintDropsWholeCache() {
        ResolutionCache.attach(store, "v1");
        ResolutionCache.remember("k", "b");
        assertEquals("b", ResolutionCache.peek("k"));

        assertFalse("指纹变了应视为不可复用", ResolutionCache.attach(store, "v2"));
        assertNull(ResolutionCache.peek("k"));
        assertArrayEquals(new String[]{"a", "b"},
                ResolutionCache.prioritize("k", new String[]{"a", "b"}));
    }

    @Test
    public void sameFingerprintKeepsCache() {
        ResolutionCache.attach(store, "v1");
        ResolutionCache.remember("k", "b");

        assertTrue(ResolutionCache.attach(store, "v1"));
        assertEquals("b", ResolutionCache.peek("k"));
    }

    @Test
    public void resolverRecordsWhichCandidateWon() throws Exception {
        ResolutionCache.attach(store, "v1");
        assertTrue(CandidateRegistry.install(
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"t.m\":[\"a\",\"b\"]}}"));

        TargetResolver.findMethodByCandidates(Names.class, "t.m", false,
                method -> "b".equals(method.getName()));

        assertEquals("b", ResolutionCache.peek("t.m"));
    }

    @Test
    public void resolverStillVerifiesWhenCacheSuggestsCandidate() throws Exception {
        ResolutionCache.attach(store, "v1");
        ResolutionCache.remember("t.m", "b");
        assertTrue(CandidateRegistry.install(
                "{\"schemaVersion\":1,\"revision\":1,\"symbols\":{\"t.m\":[\"a\",\"b\"]}}"));

        // 缓存指向 b，但验证器只认 a：缓存只能改顺序，不能替验证器做主
        assertEquals("a", TargetResolver.findMethodByCandidates(Names.class, "t.m", false,
                method -> "a".equals(method.getName())).getName());
    }

    static class Names {
        public void a() {
        }

        public void b() {
        }
    }

    static final class MemStore implements ResolutionCache.Store {
        private final Map<String, String> map = new HashMap<>();

        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public void clear() {
            map.clear();
        }
    }
}
