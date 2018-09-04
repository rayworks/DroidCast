package com.rayworks.droidcast;

import android.graphics.Bitmap;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Created by seanzhou on 3/14/17. */
public class ScreenCaptor {
    public static Bitmap screenshot(int w, int h) {
        Bitmap bitmap = null;

        try {
            System.out.println("started");

            String className;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) {
                className = "android.view.SurfaceControl";
            } else {
                className = "android.view.Surface";
            }

            Method declaredMethod =
                    Class.forName(className)
                            .getDeclaredMethod("screenshot", Integer.TYPE, Integer.TYPE);
            bitmap = (Bitmap) declaredMethod.invoke(null, new Object[] {w, h});

            System.out.println(">>> bmp generated.");
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
