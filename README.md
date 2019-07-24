# DroidCast

[中文文档](/README_CN.md)

An experimental demo for capturing and displaying screenshot of Android devices in the WebBrowser.

## Dependencies
* [Python 2.x](https://www.python.org/downloads/)
* [ADB tool](https://developer.android.com/studio/releases/platform-tools)

## Quick start
* Connect your device/emulator

* Install the apk
 
 Download and install the prebuilt apk from [here](/apk/DroidCast-debug-1.0.apk) or install it directly:
```
./gradlew clean installDebug
```
* Run the script
```
python automation.py
```

After that, the default web browser will be opened. You should see the screenshot now. 

## Use it wirelessly :

* Get your device IP address (in Settings - System - About phone - Status) e.g : `192.168.x.x`
* Enable adb over TCP/IP on your device: `adb tcpip 5555`
* Connect to your device: `adb connect 192.168.x.x:5555` (replace `192.168.x.x` with the actual IP address)
* Unplug your device
* Go through all the steps under [Common usage](#usage)

To switch back to USB mode: `adb usb`.

<h2 id="usage">Common usage:</h2>

### Note:
Once apk file installed, you can use the [cmd_tool](/automation.py) to automate the following `adb` related operations.

* Install the apk properly on the phone (Don't install it via `Run 'app'` from Android Studio).

```
./gradlew clean installDebug
```

* Push the apk to the `tmp` folder
```
adb push ${your-project-path}/DroidCast/app/build/outputs/apk/debug/DroidCast-debug-1.0.apk /data/local/tmp
```

* Start our internal server process for image processing by `app_process`  
```
$ adb shell
D1C:/ $ export CLASSPATH=/data/local/tmp/DroidCast-debug-1.0.apk
D1C:/ $ exec app_process /system/bin com.rayworks.droidcast.Main '$@'
>>> DroidCast main entry
```

![](/process_main.png)

* Please note: On some devices, 
if you got the error "appproc: ERROR: could not find class 'com.rayworks.droidcast.Main', please replace the 
above value of `CLASSPATH` with the result returned by `adb shell pm path com.rayworks.droidcast`.

![](/apk_src_path.png)

* Use `adb` forward socket connection from your pc to the connected device
```
$ adb forward tcp:53516 tcp:53516
```

* View the image via web browser
http://localhost:53516/screenshot or with the specific dimension and image format,
e.g. http://localhost:53516/screenshot?format=png&width=1080&height=1920

 Currently `png`, `jpeg` and `webp`, these image types are supported. 

 ![](/screen_shot.png)


## Reference <br>

[vysor原理以及Android同屏方案](http://zke1ev3n.me/2016/07/02/vysor%E5%8E%9F%E7%90%86%E4%BB%A5%E5%8F%8AAndroid%E5%90%8C%E5%B1%8F%E6%96%B9%E6%A1%88/)

[scrcpy : Display and control your Android device](https://github.com/Genymobile/scrcpy)

## Alternatives

[scrcpy](https://github.com/Genymobile/scrcpy)

[web-adb](https://github.com/mfinkle/web-adb)

[AndroidScreenShot_SysApi](https://github.com/weizongwei5/AndroidScreenShot_SysApi)

## License
```
Copyright (C) 2018 rayworks

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

