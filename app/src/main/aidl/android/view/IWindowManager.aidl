package android.view;

import android.graphics.Point;

/***
 * Define this existing aidl file to mock the system hidden APIs.
 * NB: the system's actual IWindowManager will be used.
 */
interface IWindowManager {

    void getInitialDisplaySize(int displayId, out Point size);

    void getBaseDisplaySize(int displayId, out Point size);

    void getRealDisplaySize(out Point paramPoint);

    /**
     * Retrieve the current orientation of the primary screen.
     * @return Constant as per {@link android.view.Surface.Rotation}.
     *
     * @see android.view.Display#DEFAULT_DISPLAY
     */
    int getDefaultDisplayRotation();
}
