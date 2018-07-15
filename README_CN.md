# DroidCast
在 Android 设备上截屏并在PC上显示屏幕图像的一个实验性项目。


### 备注:
如果开发环境是类 `Unix` 的操作系统，一旦APK文件生成，你可以使用 [命令行工具](/cmd_tool/cmd_runner.c) 来简化以下 `adb` 命令相关的操作. 

## 使用说明:

* 在手机上安装 apk

```
./gradlew clean installDebug
```

* 将 apk 文件 push 到手机的 `tmp` 文件夹下
```
adb push ${your-project-path}/DroidCast/app/build/outputs/apk/debug/DroidCast-debug-1.0.apk /data/local/tmp
```

* 通过 `app_process` 启动内部的图片处理服务进程
```
$ adb shell
D1C:/ $ export CLASSPATH=/data/local/tmp/DroidCast-debug-1.0.apk
D1C:/ $ exec app_process /system/bin com.rayworks.droidcast.Main '$@'
>>> DroidCast main entry
```

![](/process_main.png)

* 使用 `adb forward` 命令将本地（PC）`socket` 连接重定向到远端已连接的设备（手机）上。
```
$ adb forward tcp:53516 tcp:53516
```

* 在 PC 上打开浏览器查看截图
http://localhost:53516/screenshot.jpg

 ![](/screen-shot.png)


## 参考:

[vysor原理以及Android同屏方案](http://zke1ev3n.me/2016/07/02/vysor%E5%8E%9F%E7%90%86%E4%BB%A5%E5%8F%8AAndroid%E5%90%8C%E5%B1%8F%E6%96%B9%E6%A1%88/)

[scrcpy : Display and control your Android device](https://github.com/Genymobile/scrcpy)

## License

```Copyright (C) 2018 rayworks

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


