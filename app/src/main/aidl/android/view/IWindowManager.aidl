package android.view;

import android.graphics.Point;
import android.view.IRotationWatcher;

/***
 * Define this existing aidl file to mock the system hidden APIs.
 * NB: the system's actual IWindowManager will be used.
 */
interface IWindowManager {

    void getInitialDisplaySize(int displayId, out Point size);

    void getBaseDisplaySize(int displayId, out Point size);

    void getRealDisplaySize(out Point paramPoint);

    /**
     * Remove a rotation watcher set using watchRotation.
     * @hide
     */
    void removeRotationWatcher(IRotationWatcher watcher);
}
