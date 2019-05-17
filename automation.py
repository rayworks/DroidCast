#!/usr/local/bin/python -u

# Script (written in Python 2.7.15) to automate the configurations to show the screenshot on your
# default web browser.
# To get started, simply run : 'python ./automation.py'

import os
import subprocess
import webbrowser

from threading import Timer

adb = ['adb']


def run_adb(args, pipeOutput=True):
    args = adb + args

    out = None
    if (pipeOutput):
        out = subprocess.PIPE

    p = subprocess.Popen([str(arg)
                          for arg in args], stdout=out)
    stdout, stderr = p.communicate()
    return (p.returncode, stdout, stderr)


def locateApkPath():
    (rc, out, _) = run_adb(["shell", "pm",
                            "path",
                            "com.rayworks.droidcast"])
    if (rc):
        raise RuntimeError("Locating apk failure")

    prefix = "package:"
    postfix = ".apk"
    beg = out.index(prefix, 0)
    end = out.rfind(postfix)

    return "CLASSPATH=" + out[beg + len(prefix):(end + len(postfix))].strip()


def openBrowser():
    url = 'http://localhost:53516/screenshot'
    webbrowser.open_new(url)


def automate():
    try:
        class_path = locateApkPath()

        (code, out, err) = run_adb(["forward", "tcp:53516", "tcp:53516"])
        print(">>> adb forward tcp:53516 ", code)

        args = ["shell",
                class_path,
                "app_process",
                "/",  # unused
                "com.rayworks.droidcast.Main"]
        print(args)

        # delay opening the web page
        t = Timer(2, openBrowser)
        t.start()

        # event loop starts
        run_adb(args, pipeOutput=False)

        (code, out, err) = run_adb(["forward", "--remove", "tcp:53516"])
        print(">>> adb unforward tcp:53516 ", code)

    except (Exception), e:
        print e


if __name__ == "__main__":
    automate()
