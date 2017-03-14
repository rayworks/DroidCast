package com.rayworks.droidcast;

import android.graphics.Bitmap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by seanzhou on 3/14/17.
 */

public class ScreenCaptor {
    public static Bitmap screenshot(int w, int h) {
        Bitmap bitmap = null;

        try {
            System.out.println("started");
            Method declaredMethod = Class.forName("android.view.SurfaceControl").getDeclaredMethod("screenshot", new Class[]{Integer.TYPE, Integer.TYPE});
            bitmap = (Bitmap) declaredMethod.invoke(null, new Object[]{Integer.valueOf(w), Integer.valueOf(h)});

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
