package com.yy.mobile.widget;

import android.animation.ValueAnimator;
import android.support.annotation.NonNull;
import java.lang.reflect.Field;

/**
 * 修改属性动画时长工具类
 * 来自 <a href="https://blog.csdn.net/u011387817/article/details/78628956">https://blog.csdn.net/u011387817/article/details/78628956</a>
 */
public class ValueAnimatorUtil {

    /**
     * 如果动画被禁用，则重置动画缩放时长
     */
    public static void resetDurationScaleIfDisable() {
        if (getDurationScale() == 0) resetDurationScale();
    }

    /**
     * 重置动画缩放时长
     */
    public static void resetDurationScale() {
        try {
            getField().setFloat(null, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static float getDurationScale() {
        try {
            return getField().getFloat(null);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @NonNull
    private static Field getField() throws NoSuchFieldException {
        Field field = ValueAnimator.class.getDeclaredField("sDurationScale");
        field.setAccessible(true);
        return field;
    }
}