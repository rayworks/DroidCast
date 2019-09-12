![](./cast.png)

# DroidCast

在 Android 设备上截屏并在浏览器上显示屏幕图像的一个实验性项目。

![](/screen_shot_dock.png)

## 依赖

- [Python](https://www.python.org/downloads/)
- [ADB tool](https://developer.android.google.cn/studio/releases/platform-tools)

## 快速上手

- 连接你的设 Android 设备或是模拟器

- 安装 Apk

从 [这里](/apk/DroidCast-debug-1.0.apk) 下载并安装应用，或是直接通过命令行安装:

```
./gradlew clean installDebug
```

- 运行脚本

```
python scripts/automation.py
```

然后，默认浏览器将会打开，你就能看到截屏了。

## 同网段 WIFI 环境下无线配置使用：

- 获取设备 IP 地址 (设置-系统-关于手机-状态) e.g : `192.168.x.x`
- 在设备上配置启用 adb 的 TCP/IP 模式 : `adb tcpip 5555`
- 连接你的设备 : `adb connect 192.168.x.x:5555`(替换`192.168.x.x`为设备实际 IP)
- 拔掉 USB 连接线
- 按下面的 [一般（USB 连线）使用说明](#usage) 中的步骤完成设置

如需重置为默认的 USB 模式，请执行 : `adb usb`

<h2 id="usage">一般（USB 连线）使用说明:</h2>

### 备注:

一旦 APK 安装完成，你可以使用 [python 脚本](/scripts/automation.py) 来自动化完成以下 `adb` 命令相关的操作.

- 执行以下命令在手机上安装 apk （不要通过 Android Studio `Run 'app'` 来安装）

```
./gradlew clean installDebug
```

- 将 apk 文件 push 到手机的 `tmp` 文件夹下

```
adb push ${your-project-path}/DroidCast/app/build/outputs/apk/debug/DroidCast-debug-1.0.apk /data/local/tmp
```

- 通过 `app_process` 启动内部的图片处理服务进程

```
$ adb shell
D1C:/ $ export CLASSPATH=/data/local/tmp/DroidCast-debug-1.0.apk
D1C:/ $ exec app_process /system/bin com.rayworks.droidcast.Main '$@'
>>> DroidCast main entry
```

![](/process_main.png)

- 请注意: 在某些设备上, 如果你碰到类似 "appproc: ERROR: could not find class 'com.rayworks.droidcast.Main'的错误，
  请把上面 shell 命令中的 `CLASSPATH` 值替换为执行命令 `adb shell pm path com.rayworks.droidcast` 后的实际返回值。

![](/apk_src_path.png)

- 使用 `adb forward` 命令将本地（PC）`socket` 连接重定向到远端已连接的设备（手机）上。

```
$ adb forward tcp:53516 tcp:53516
```

- 在 PC 上打开浏览器查看截图
  http://localhost:53516/screenshot
  或者按指定的图片大小和类型查看，如
  http://localhost:53516/screenshot?format=png&width=1080&height=1920
  目前支持的图片类型有`png`, `jpeg` 以及 `webp`。

## 参考

[vysor 原理以及 Android 同屏方案](https://juejin.im/entry/57fe39400bd1d00058dd4652)

[scrcpy : Display and control your Android device](https://github.com/Genymobile/scrcpy)

## 相关项目

[scrcpy](https://github.com/Genymobile/scrcpy)

[web-adb](https://github.com/mfinkle/web-adb)

[AndroidScreenShot_SysApi](https://github.com/weizongwei5/AndroidScreenShot_SysApi)

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
