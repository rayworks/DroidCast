package android.view;

import android.graphics.Point;
import android.view.IRotationWatcher;

/***
 * Define this same aidl file here to make it compiling when calling system hidden APIs.
 *
 * According to the 'delegation model' for loading classes on Java plattform, the actual
 * implementation of IWindowManager from the Framework will be applied.
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
