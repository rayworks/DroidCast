#!/usr/local/bin/python -u

# Script (written in Python 2.7.15) to automate the configurations to show the screenshot on your
# default web browser.
# To get started, simply run : 'python ./automation.py'

import os
import subprocess
import webbrowser
import argparse

from threading import Timer

adb = ['adb']
device_serial_no = ''

parser = argparse.ArgumentParser(description='Automation script to activate capturing screenshot of Android device')
parser.add_argument('-s', '--serial', dest='device_serial', help='Device serial number (adb -s option)')
args_in = parser.parse_args()

def run_adb(args, pipeOutput=True):
    if(args_in.device_serial):
        args = adb + ['-s', args_in.device_serial] + args
    else:
        args = adb + args

    # print('exec cmd : %s' % args)
        
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

def identifyDevice():

    (rc, out, _) = run_adb(["devices"])
    if(rc):
        raise RuntimeError("Fail to find devices")
    else:
        print out

        device_serial_no = ''
        if(args_in.device_serial):
            device_serial_no = args_in.device_serial
        
        # Output as following:
        # List of devices attached
        # 6466eb0c	device
        devicesInfo = str(out)
        deviceCnt = devicesInfo.count('device') - 1
        if(deviceCnt > 1 and device_serial_no == ''):
            raise RuntimeError("Please specify the serial number of target device you want to use ('-s serial_number').")

def automate():
    try:
        identifyDevice()

        class_path = locateApkPath()

        (code, out, err) = run_adb(["forward", "tcp:53516", "tcp:53516"])
        print(">>> adb forward tcp:53516 ", code)

        args = ["shell",
                class_path,
                "app_process",
                "/",  # unused
                "com.rayworks.droidcast.Main"]

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
