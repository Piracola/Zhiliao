package com.shatyuka.zhiliao.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** 评论广告的验证器必须真的在验：模型得是广告卡片模型的子类，绑定方法得是吃这个模型的实例方法。 */
public class CommentAdValidatorTest {
    public static class FakeAdBase {
    }

    public static class FakeAdModel extends FakeAdBase {
        public int position;
    }

    public static class NotAnAdModel {
    }

    public static abstract class AbstractAdModel extends FakeAdBase {
    }

    public static class InstanceHolder extends FakeAdBase {
        public void bind(FakeAdModel model) {
        }
    }

    public static class StaticHolder extends FakeAdBase {
        public static void bind(FakeAdModel model) {
        }
    }

    public static class NonBindHolder extends FakeAdBase {
        public void bind(String model) {
        }
    }

    private static final String[] BASES = {FakeAdBase.class.getName()};

    @Before
    public void reset() {
        CommentAd.CommentListAd = null;
    }

    @Test
    public void modelMustExtendAdCardBase() {
        assertTrue(CommentAd.looksLikeAdCardModel(FakeAdModel.class, BASES));
        assertFalse("与广告模型无关的类不能因为公开、非抽象就过关",
                CommentAd.looksLikeAdCardModel(NotAnAdModel.class, BASES));
        assertFalse("基类本身不是评论广告模型",
                CommentAd.looksLikeAdCardModel(FakeAdBase.class, BASES));
        assertFalse("抽象类不能当模型",
                CommentAd.looksLikeAdCardModel(AbstractAdModel.class, BASES));
        assertFalse(CommentAd.looksLikeAdCardModel(Object.class, BASES));
    }

    @Test
    public void unknownBaseNeverMatches() {
        assertFalse(CommentAd.looksLikeAdCardModel(FakeAdModel.class,
                new String[]{"not.on.classpath.Missing"}));
        assertFalse(CommentAd.looksLikeAdCardModel(FakeAdModel.class, new String[0]));
    }

    @Test
    public void bindMethodMustBeInstanceAndTakeTheModel() {
        CommentAd.CommentListAd = FakeAdModel.class;
        assertTrue(CommentAd.looksLikeAdViewHolder(InstanceHolder.class, FakeAdBase.class));
        assertFalse("static 绑定拿不到 thisObject，hook 里就取不了 itemView",
                CommentAd.looksLikeAdViewHolder(StaticHolder.class, FakeAdBase.class));
        assertFalse(CommentAd.looksLikeAdViewHolder(NonBindHolder.class, FakeAdBase.class));
        assertFalse("基类自身不是 ViewHolder",
                CommentAd.looksLikeAdViewHolder(FakeAdBase.class, FakeAdBase.class));
    }
}
