package com.rayworks.droidcast.wrapper;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/***
 * Code taken and modified from
 * <a href="https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java">scrcpy</a>
 */
@SuppressLint({"BlockedPrivateApi", "PrivateApi"})
public final class DisplayControl {

    private static final Class<?> CLASS;

    static {
        // On Android 14, execute a separate process with a different classpath and LD_PRELOAD to execute the methods
        // required to take a screenshot
        // https://github.com/Genymobile/scrcpy/pull/4446#issuecomment-1824818046
        Class<?> displayControlClass = null;
        try {
            Class<?> classLoaderFactoryClass = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method createClassLoaderMethod = classLoaderFactoryClass.getDeclaredMethod("createClassLoader", String.class, String.class, String.class,
                    ClassLoader.class, int.class, boolean.class, String.class);
            ClassLoader classLoader = (ClassLoader) createClassLoaderMethod.invoke(null, "/system/framework/services.jar", null, null,
                    ClassLoader.getSystemClassLoader(), 0, true, null);

            displayControlClass = classLoader.loadClass("com.android.server.display.DisplayControl");

            Method loadMethod = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            loadMethod.setAccessible(true);
            loadMethod.invoke(Runtime.getRuntime(), displayControlClass, "android_servers");
        } catch (Throwable e) {
            System.err.printf("Could not initialize DisplayControl : %s\n", e);
            // Do not throw an exception here, the methods will fail when they are called
        }
        CLASS = displayControlClass;
    }

    private static Method getPhysicalDisplayTokenMethod;
    private static Method getPhysicalDisplayIdsMethod;

    private DisplayControl() {
        // only static methods
    }

    private static Method getGetPhysicalDisplayTokenMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayTokenMethod == null) {
            getPhysicalDisplayTokenMethod = CLASS.getMethod("getPhysicalDisplayToken", long.class);
        }
        return getPhysicalDisplayTokenMethod;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static IBinder getPhysicalDisplayToken(long physicalDisplayId) {
        try {
            Method method = getGetPhysicalDisplayTokenMethod();
            return (IBinder) method.invoke(null, physicalDisplayId);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            System.err.printf("Could not invoke method : %s\n", e);
            return null;
        }
    }

    private static Method getGetPhysicalDisplayIdsMethod() throws NoSuchMethodException {
        if (getPhysicalDisplayIdsMethod == null) {
            getPhysicalDisplayIdsMethod = CLASS.getMethod("getPhysicalDisplayIds");
        }
        return getPhysicalDisplayIdsMethod;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static long[] getPhysicalDisplayIds() {
        try {
            Method method = getGetPhysicalDisplayIdsMethod();
            return (long[]) method.invoke(null);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            System.err.printf("Could not invoke method : %s\n", e);
            return null;
        }
    }
}
