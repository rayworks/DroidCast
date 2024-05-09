package com.rayworks.droidcast;

import org.junit.Assert;
import org.junit.Test;

import kotlin.Pair;

public class MiscUtilsTest {
    @Test
    public void testParsingDisplayInfo() {
        Pair<int[], Long> pair = MiscUtils.parseDisplayInfo("brightnessDefault 400.0}\n" +
                "    mOverrideDisplayInfo=DisplayInfo{\"HDMI 屏幕\", displayId 13\", displayGroupId 0, FLAG_SECURE, " +
                "FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_PRESENTATION, FLAG_TRUSTED, real 3840 x 2160, largest app 3840 " +
                "x 3840, smallest app 2160 x 2160, appVsyncOff 1000000, presDeadline 16666666, mode 216, defaultMode " +
                "216, modes [{id=216, width=3840, height=2160, fps=60.000004, alternativeRefreshRates=[24.000002, 25.0]}," +
                " {id=217, width=3840, height=2160, fps=25.0, alternativeRefreshRates=[24.000002, 60.000004]}, {id=218, " +
                "width=3840, height=2160, fps=24.000002, alternativeRefreshRates=[25.0, 60.000004]}, {id=219, width=1920," +
                " height=2160, fps=60.000004, alternativeRefreshRates=[]}, {id=220, width=2560, height=1440, fps=60.000004," +
                " alternativeRefreshRates=[]}, {id=221, width=2048, height=1280, fps=60.000004, alternativeRefreshRates=[]}, " +
                "{id=222, width=1920, height=1080, fps=60.000004, alternativeRefreshRates=[24.000002, 25.0, 50.0]}, {id=223, " +
                "width=1920, height=1080, fps=50.0, alternativeRefreshRates=[24.000002, 25.0, 60.000004]}, {id=224, width=1920," +
                " height=1080, fps=25.0, alternativeRefreshRates=[24.000002, 50.0, 60.000004]}, {id=225, width=1920, height=1080," +
                " fps=24.000002, alternativeRefreshRates=[25.0, 50.0, 60.000004]}, {id=226, width=1600, height=1200, fps=60.000004," +
                " alternativeRefreshRates=[]}, {id=227, width=1600, height=900, fps=60.000004, alternativeRefreshRates=[]}, {id=228," +
                " width=1280, height=1024, fps=75.0, alternativeRefreshRates=[60.000004]}, {id=229, width=1280, height=1024, " +
                "fps=60.000004, alternativeRefreshRates=[75.0]}, {id=230, width=1152, height=864, fps=75.0, alternativeRefreshRates=[]}," +
                " {id=231, width=1280, height=720, fps=60.000004, alternativeRefreshRates=[50.0]}, {id=232, width=1280, height=720, fps=50.0, " +
                "alternativeRefreshRates=[60.000004]}, {id=233, width=1024, height=768, fps=75.0, alternativeRefreshRates=[60.000004]}, " +
                "{id=234, width=1024, height=768, fps=60.000004, alternativeRefreshRates=[75.0]}, {id=235, width=800, height=600, fps=75.0," +
                " alternativeRefreshRates=[60.000004]}, {id=236, width=800, height=600, fps=60.000004, alternativeRefreshRates=[75.0]}, " +
                "{id=237, width=720, height=576, fps=50.0, alternativeRefreshRates=[]}, {id=238, width=720, height=480, fps=60.000004, " +
                "alternativeRefreshRates=[]}, {id=239, width=640, height=480, fps=75.0, alternativeRefreshRates=[60.000004]}, " +
                "{id=240, width=640, height=480, fps=60.000004, alternativeRefreshRates=[75.0]}, {id=241, width=720, height=400, " +
                "fps=70.0, alternativeRefreshRates=[]}], hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[2, 3, 4], " +
                "mMaxLuminance=500.0, mMaxAverageLuminance=250.0, mMinLuminance=0.0}, userDisabledHdrTypes []," +
                " minimalPostProcessingSupported false, rotation 0, state ON, type EXTERNAL, uniqueId \"local:4616378901338450436\", " +
                "app 3840 x 2160, density 213 (162.56 x 161.364) dpi, layerStack 13, colorMode 0, supportedColorModes [0], " +
                "address {port=4, model=0x4010ac26f2e2ea}, deviceProductInfo DeviceProductInfo{name=DELL S2722QC, " +
                "manufacturerPnpId=DEL, productId=41424, modelYear=null, manufactureDate=ManufactureDate{week=15, " +
                "year=2023}, connectionToSinkType=0}, removeMode 0, refreshRateOverride 0.0, brightnessMinimum 0.0, " +
                "brightnessMaximum 1.0, brightnessDefault 400.0}", 13);

        Assert.assertNotNull(pair);
        int[] dimens = pair.component1();
        Assert.assertEquals(3840, dimens[0]);
        Assert.assertEquals(2160, dimens[1]);

        Assert.assertEquals(4616378901338450436L, pair.component2().longValue());
    }
}
