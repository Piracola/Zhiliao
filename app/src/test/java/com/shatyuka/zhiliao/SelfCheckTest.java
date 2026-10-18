package com.shatyuka.zhiliao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** 运行期自证只做记账，纯逻辑，JVM 里就能验。 */
public class SelfCheckTest {

    @Before
    public void setUp() {
        SelfCheck.reset();
    }

    @Test
    public void fireIsRecordedOncePerFeatureAndTarget() {
        assertTrue(SelfCheck.fire("去信息流广告", "com.zhihu.android.a#b"));
        assertFalse(SelfCheck.fire("去信息流广告", "com.zhihu.android.a#b"));
        assertTrue(SelfCheck.fire("去搜索广告", "com.zhihu.android.a#b"));
        assertEquals(2, SelfCheck.firedCount());
    }

    @Test
    public void summaryListsTargetsThatNeverFired() {
        SelfCheck.installed("去信息流广告", "com.zhihu.android.a#b");
        SelfCheck.installed("去搜索广告", "com.zhihu.android.c#d");
        SelfCheck.fire("去信息流广告", "com.zhihu.android.a#b");

        assertEquals("已装 2 个目标，未触发 1 个：去搜索广告 com.zhihu.android.c#d", SelfCheck.summary());
    }

    @Test
    public void summaryReportsAllFired() {
        SelfCheck.installed("反追踪", "com.zhihu.android.e#f");
        SelfCheck.fire("反追踪", "com.zhihu.android.e#f");

        assertEquals("已装 1 个目标，全部触发过", SelfCheck.summary());
    }

    @Test
    public void summaryHandlesEmptyRegistry() {
        assertEquals("尚未安装任何目标", SelfCheck.summary());
    }

    @Test
    public void installedWithoutFireStaysPending() {
        SelfCheck.installed("禁止自动刷新", "com.zhihu.android.g#h");

        assertEquals(1, SelfCheck.installedCount());
        assertEquals(0, SelfCheck.firedCount());
        assertEquals(1, SelfCheck.neverFired().size());
    }
}
