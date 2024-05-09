package com.rayworks.droidcast.shell;

import com.rayworks.droidcast.MiscUtils;

import kotlin.Pair;

public final class ShellRunner {

    /***
     * Retrieves the {@link android.view.Display} dimensions and physical id
     *
     * @param displayId specified display id
     * @return Display's dimensions and physical id or <code>null</code> if not found.
     */
    public static Pair<int[], Long> getDisplayDimension(int displayId) {
        try {
            String dumpsysDisplayOutput = Command.execReadOutput("dumpsys", "display");
            return MiscUtils.parseDisplayInfo(dumpsysDisplayOutput, displayId);
        } catch (Exception e) {
            System.err.println("Could not get display info from \"dumpsys display\" output" + e);
            return null;
        }
    }
}
