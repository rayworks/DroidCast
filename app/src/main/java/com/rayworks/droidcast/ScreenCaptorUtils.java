package com.rayworks.droidcast;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Created by seanzhou on 3/14/17. */
public class ScreenCaptorUtils {

    private static final String METHOD_SCREENSHOT = "screenshot";

    public static Bitmap screenshot(int w, int h) {
        Bitmap bitmap = null;

        try {
            String className;
            int sdkInt = Build.VERSION.SDK_INT;
            if (sdkInt > Build.VERSION_CODES.JELLY_BEAN_MR1) {
                className = "android.view.SurfaceControl";
            } else {
                className = "android.view.Surface";
            }

            Method declaredMethod;
            Class<?> clazz = Class.forName(className);

            if (sdkInt >= Build.VERSION_CODES.P) { // Pie+
                declaredMethod =
                        clazz.getDeclaredMethod(
                                METHOD_SCREENSHOT,
                                Rect.class,
                                Integer.TYPE,
                                Integer.TYPE,
                                Integer.TYPE);
                bitmap = (Bitmap) declaredMethod.invoke(null, new Rect(), w, h, 0);
            } else {
                declaredMethod =
                        clazz.getDeclaredMethod(METHOD_SCREENSHOT, Integer.TYPE, Integer.TYPE);
                bitmap = (Bitmap) declaredMethod.invoke(null, new Object[] {w, h});
            }

            if (bitmap != null) System.out.println(">>> bmp generated.");

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return bitmap;
    }
}
