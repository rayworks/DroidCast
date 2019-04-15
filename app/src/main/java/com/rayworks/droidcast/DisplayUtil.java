package com.rayworks.droidcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.IWindowManager;

import java.lang.reflect.Method;

/**
 * Created by Sean on 5/27/17.
 */
/* package */ final class DisplayUtil {

    private IWindowManager iWindowManager;

    public DisplayUtil() {
        Class<?> serviceManagerClass = null;

        try {
            serviceManagerClass = Class.forName("android.os.ServiceManager");

            Method getService = serviceManagerClass.getDeclaredMethod("getService", String.class);

            // WindowManager
            Object ws = getService.invoke(null, Context.WINDOW_SERVICE);

            Object localObject = IWindowManager.Stub.asInterface((IBinder) ws);
            iWindowManager = (IWindowManager) localObject;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * * Retrieves the device actual display size.
     *
     * @return {@link Point}
     */
    Point getCurrentDisplaySize() {
        try {
            Point localPoint = new Point();

            // Resolve the screen resolution for devices with OS version 4.3+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                iWindowManager.getInitialDisplaySize(0, localPoint);
            }

            System.out.println(">>> Dimension: " + localPoint);
            return localPoint;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Retrieve the current orientation of the primary screen.
     *
     * @return Constant as per {@link android.view.Surface.Rotation}.
     * @see android.view.Display#DEFAULT_DISPLAY
     */
    int getScreenRotation() {
        int rotation = 0;

        try {
            Class<?> cls = iWindowManager.getClass();
            try {
                rotation = (Integer) iWindowManager.getClass().getMethod("getRotation").invoke(iWindowManager);
            } catch (NoSuchMethodException e) {
                rotation = (Integer) cls.getMethod("getDefaultDisplayRotation").invoke(iWindowManager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(">>> Screen rotation: " + rotation);

        return rotation;
    }

    Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);

        return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
