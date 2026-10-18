package com.shatyuka.zhiliao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * 自定义过滤的入口守卫。灾难性回溯能把知乎进程整个拖死，
 * 所以超长正则必须在进 Pattern 之前就被挡住，而不是"编译成功了就算过"。
 */
public class RegexGuardTest {

    @Test
    public void overlongPatternIsRejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= Helper.MAX_REGEX_LENGTH; i++) {
            sb.append('a');
        }
        assertNull(Helper.compileRegex(sb.toString()));
    }

    @Test
    public void patternAtTheLimitStillCompiles() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Helper.MAX_REGEX_LENGTH; i++) {
            sb.append('a');
        }
        assertNotNull(Helper.compileRegex(sb.toString()));
    }

    @Test
    public void emptyAndBrokenPatternsAreNull() {
        assertNull(Helper.compileRegex(null));
        assertNull(Helper.compileRegex(""));
        assertNull(Helper.compileRegex("([a-z"));
        assertEquals("过滤器", Helper.compileRegex("过滤器").pattern());
    }
}
