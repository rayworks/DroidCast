package com.rayworks.droidcast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Pair;

/***
 * The Text processing utils
 */
public final class MiscUtils {
    /***
     * Retrieves the display info from 'dumpsys display' output
     *
     * @param dumpsysDisplayOutput
     * @param displayId
     * @return
     */
    public static Pair<int[], Long> parseDisplayInfo(String dumpsysDisplayOutput, int displayId) {
        Pattern regex = Pattern.compile(
                "^ {4}mOverrideDisplayInfo=DisplayInfo\\{\".*?, displayId " + displayId + ".*?(, FLAG_.*)?, real ([0-9]+) x ([0-9]+).*?, "
                        + "rotation ([0-9]+).*?, uniqueId \"local:([0-9]+).*?, layerStack ([0-9]+)",
                Pattern.MULTILINE);
        Matcher m = regex.matcher(dumpsysDisplayOutput);
        if (!m.find()) {
            return null;
        }
        int width = Integer.parseInt(m.group(2));
        int height = Integer.parseInt(m.group(3));

        // the physical display long id
        long physicalDisplayId = Long.parseLong(m.group(5));

        return new Pair<>(new int[]{width, height}, physicalDisplayId);
    }
}
