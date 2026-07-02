package com.rayworks.droidcast;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.IBinder;
import android.os.OutcomeReceiver;

import androidx.annotation.RequiresApi;

import com.rayworks.droidcast.wrapper.DisplayControl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Created by seanzhou on 3/14/17.
 */
@SuppressLint({"BlockedPrivateApi", "PrivateApi"})
public final class ScreenCaptorUtils {

    private static final String METHOD_SCREENSHOT = "screenshot";

    /** Default logical display id ({@code Display.DEFAULT_DISPLAY}). */
    private static final int DEFAULT_DISPLAY_ID = 0;

    /** Max time to block waiting for the asynchronous readback capture (Android 17+). */
    private static final long SCREENSHOT_TIMEOUT_SECONDS = 5;

    private static final Class<?> clazz;
    private static Method getBuiltInDisplayMethod;

    static {
        try {
            String className;
            int sdkInt = Build.VERSION.SDK_INT;
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                className = "android.window.ScreenCapture";
            } else if (sdkInt > Build.VERSION_CODES.JELLY_BEAN_MR1) {
                className = "android.view.SurfaceControl";
            } else {
                className = "android.view.Surface";
            }

            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static Bitmap screenshot(int w, int h) {
        Bitmap bitmap = null;

        try {
            Method declaredMethod;

            int sdkInt = Build.VERSION.SDK_INT;
            if (sdkInt >= Build.VERSION_CODES.S) {
                try {
                    // Android 12 (S) .. Android 16: DisplayCaptureArgs + captureDisplay(...)
                    bitmap = captureViaDisplayCaptureArgs(w, h);
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    // The legacy DisplayCaptureArgs / captureDisplay hidden API was removed on
                    // Android 16 QPR2 / Android 17+, where android.window.ScreenCapture was rewritten
                    // around ScreenCaptureParams + ScreenCaptureResult and an async capture() call.
                    // (This is the "android.window.ScreenCapture$DisplayCaptureArgs" ClassNotFoundException.)
                    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        System.err.println(
                                ">>> legacy captureDisplay unavailable, trying readback API : " + e);
                        bitmap = captureViaReadback();
                    } else {
                        throw e;
                    }
                }
            } else if (sdkInt >= Build.VERSION_CODES.P) { // Pie+
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
                bitmap = (Bitmap) declaredMethod.invoke(null, new Object[]{w, h});
            }

            if (bitmap != null) System.out.println(">>> bmp generated.");

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException | ClassNotFoundException | InstantiationException e) {
            e.printStackTrace();
        }

        return bitmap;
    }

    /**
     * Legacy capture path used from Android 12 (S) up to Android 16: build a {@code
     * DisplayCaptureArgs} and invoke the hidden synchronous {@code captureDisplay(DisplayCaptureArgs)},
     * which returns a {@code ScreenshotHardwareBuffer}.
     *
     * <p>Throws {@link ClassNotFoundException} / {@link NoSuchMethodException} when this hidden API is
     * absent (e.g. Android 16 QPR2 / Android 17+), signalling the caller to fall back to
     * {@link #captureViaReadback()}.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private static Bitmap captureViaDisplayCaptureArgs(int w, int h)
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InstantiationException, InvocationTargetException {
        // create the DisplayCaptureArgs object by DisplayCaptureArgs$Builder.build()
        Class<?> argsClass;
        Class<?> innerClass;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            argsClass = Class.forName("android.window.ScreenCapture$DisplayCaptureArgs");
            innerClass = Class.forName("android.window.ScreenCapture$DisplayCaptureArgs$Builder");
        } else {
            argsClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs");
            innerClass = Class.forName("android.view.SurfaceControl$DisplayCaptureArgs$Builder");
        }

        Method setSzMethod = innerClass.getDeclaredMethod("setSize", int.class, int.class);
        Method buildMethod = innerClass.getDeclaredMethod("build");

        Constructor<?> ctor = innerClass.getDeclaredConstructor(IBinder.class);
        Object builder = ctor.newInstance(getBuiltInDisplay());
        setSzMethod.invoke(builder, w, h);
        Object args = buildMethod.invoke(builder);

        // call hidden method "ScreenshotHardwareBuffer captureDisplay(DisplayCaptureArgs captureArgs)"
        Method captureDisplay = clazz.getDeclaredMethod("captureDisplay", argsClass);
        Object hdBuffer = captureDisplay.invoke(null, args);
        if (hdBuffer == null) {
            return null;
        }

        Class<?> hdBufferClass = hdBuffer.getClass();
        ColorSpace colorSpace =
                (ColorSpace) hdBufferClass.getDeclaredMethod("getColorSpace").invoke(hdBuffer);
        return wrapScreenshotBuffer(
                hdBufferClass.getDeclaredMethod("getHardwareBuffer").invoke(hdBuffer), colorSpace);
    }

    /**
     * Capture path for Android 16 QPR2 / Android 17+, where {@code android.window.ScreenCapture} was
     * rewritten as an async, {@code IWindowManager}-backed API:
     *
     * <pre>
     *   ScreenCaptureParams params = new ScreenCaptureParams.Builder(displayId).build();
     *   ScreenCapture.capture(params, executor, OutcomeReceiver&lt;ScreenCaptureResult, Exception&gt;);
     *   result.getHardwareBuffer(); result.getColorSpace();
     * </pre>
     *
     * The callback is bridged to a synchronous result via a {@link CountDownLatch}.
     */
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static Bitmap captureViaReadback()
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InstantiationException, InvocationTargetException {
        Class<?> screenCaptureClass = Class.forName("android.window.ScreenCapture");
        Class<?> paramsClass = Class.forName("android.window.ScreenCapture$ScreenCaptureParams");
        Class<?> builderClass =
                Class.forName("android.window.ScreenCapture$ScreenCaptureParams$Builder");
        Class<?> resultClass = Class.forName("android.window.ScreenCapture$ScreenCaptureResult");

        // ScreenCaptureParams.Builder(int displayId).build()
        Constructor<?> builderCtor = builderClass.getConstructor(int.class);
        Object builder = builderCtor.newInstance(DEFAULT_DISPLAY_ID);
        Object params = builderClass.getMethod("build").invoke(builder);

        final Object[] resultRef = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Object, Throwable> receiver =
                new OutcomeReceiver<Object, Throwable>() {
                    @Override
                    public void onResult(Object result) {
                        resultRef[0] = result;
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println(">>> readback screen capture failed : " + error);
                        latch.countDown();
                    }
                };

        // Run the callback inline on the invoking (binder) thread; the latch does the synchronising.
        Executor executor = Runnable::run;
        Method captureMethod =
                screenCaptureClass.getMethod(
                        "capture", paramsClass, Executor.class, OutcomeReceiver.class);
        captureMethod.invoke(null, params, executor, receiver);

        try {
            if (!latch.await(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println(">>> timed out waiting for readback screen capture");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        Object result = resultRef[0];
        if (result == null) {
            return null;
        }

        ColorSpace colorSpace =
                (ColorSpace) resultClass.getMethod("getColorSpace").invoke(result);
        return wrapScreenshotBuffer(
                resultClass.getMethod("getHardwareBuffer").invoke(result), colorSpace);
    }

    /** Wraps a reflectively obtained {@link HardwareBuffer} into a {@link Bitmap}. */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static Bitmap wrapScreenshotBuffer(Object hardwareBufferObj, ColorSpace colorSpace) {
        try (HardwareBuffer hardwareBuffer = (HardwareBuffer) hardwareBufferObj) {
            if (hardwareBuffer == null)
                return null;
            return Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
        }
    }

    private static Method getGetBuiltInDisplayMethod() throws NoSuchMethodException {
        if (getBuiltInDisplayMethod == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getBuiltInDisplayMethod = clazz.getMethod("getBuiltInDisplay", Integer.TYPE);
            } else { // The method signature has been changed in Android Q+
                getBuiltInDisplayMethod = clazz.getMethod("getInternalDisplayToken");
            }
        }
        return getBuiltInDisplayMethod;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static IBinder getBuiltInDisplay() {

        try {
            int sdkInt = Build.VERSION.SDK_INT;
            if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                long[] displayIds = DisplayControl.getPhysicalDisplayIds();
                if (displayIds != null) {
                    for (long id : displayIds) {
                        IBinder binder = DisplayControl.getPhysicalDisplayToken(id);
                        if (binder != null)
                            return binder;
                    }
                }

                // fall back to the default id
                return DisplayControl.getPhysicalDisplayToken(0);
            }

            Method method = getGetBuiltInDisplayMethod();
            if (sdkInt < Build.VERSION_CODES.Q) {
                // default display 0
                return (IBinder) method.invoke(null, 0);
            }

            return (IBinder) method.invoke(null);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            System.err.println("Failed to invoke method " + e);
            return null;
        }
    }
}
