# DroidCast

An experimental demo for capturing and displaying screenshot of Android devices.

Usage:
------

* Install the apk properly on the phone

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

* Use `adb` forward socket connection from your pc to the connected device
```
$ adb forward tcp:53516 tcp:53516
```

* View the image via web browser
http://localhost:53516/screenshot.jpg

 ![](/screen-shot.png)


Reference: <br>
------
[vysor原理以及Android同屏方案](http://zke1ev3n.me/2016/07/02/vysor%E5%8E%9F%E7%90%86%E4%BB%A5%E5%8F%8AAndroid%E5%90%8C%E5%B1%8F%E6%96%B9%E6%A1%88/)

## License

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