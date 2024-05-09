#!/usr/local/bin/python -u

# Script (generated for Python 3.6+) to automate the configurations to show the screenshot on your
# default web browser.
# To get started, simply run : 'python ./automation.py'

import subprocess
import webbrowser
import argparse
import signal

from threading import Timer

adb = ['adb']

parser = argparse.ArgumentParser(
    description='Automation script to activate capturing screenshot of Android device')
parser.add_argument('-s', '--serial', dest='device_serial',
                    help='Device serial number (adb -s option)')
parser.add_argument(
    '-p',
    '--port',
    dest='port',
    nargs='?',
    const=53516,
    type=int,
    default=53516,
    help='Port number to be connected, by default it\'s 53516')

parser.add_argument (
    '-d',
    '--display_id',
    dest='display_id',
    nargs='?',
    const=0,
    type=int,
    default=0,
    help='Display Id, by default it\'s 0, the internal display.')

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
                          for arg in args], stdout=out, encoding='utf-8')
    stdout, stderr = p.communicate()
    return (p.returncode, stdout, stderr)


def locate_apk_path():
    (rc, out, _) = run_adb(["shell", "pm",
                            "path",
                            "com.rayworks.droidcast"])
    if rc or out == "":
        raise RuntimeError(
            "Locating apk failure, have you installed the app successfully?")

    prefix = "package:"
    postfix = ".apk"
    beg = out.index(prefix, 0)
    end = out.rfind(postfix)

    return "CLASSPATH=" + out[beg + len(prefix):(end + len(postfix))].strip()


def open_browser():
    url = 'http://localhost:%d/screenshot' % args_in.port
    webbrowser.open_new(url)


def identify_device():

    (rc, out, _) = run_adb(["devices"])
    if(rc):
        raise RuntimeError("Fail to find devices")
    else:
        # Output as following:
        # List of devices attached
        # 6466eb0c	device
        print(out)
        device_serial_no = args_in.device_serial

        devicesInfo = str(out)
        deviceCnt = devicesInfo.count('device') - 1

        if deviceCnt < 1:
            raise RuntimeError("Fail to find devices")

        if(deviceCnt > 1 and (not device_serial_no)):
            raise RuntimeError(
                "Please specify the serial number of target device you want to use ('-s serial_number').")


def print_url():
    # ip route:
    # e.g. 192.168.0.0/24 dev wlan0 proto kernel scope link src 192.168.0.125
    (rc, out, _) = run_adb(
        ["shell", "ip route | awk '/wlan*/{ print $9 }'| tr -d '\n'"])
    print(
        ("\n>>> Share the url 'http://%s:%d/screenshot' to see the live screen! <<<\n") %
        (out, args_in.port))


def handler(signum, frame):
    print('\n>>> Signal caught: ', signum)
    (code, out, err) = run_adb(
        ["forward", "--remove", "tcp:%d" % args_in.port])
    print(">>> adb unforward tcp:%d " % args_in.port, code)


def automate():
    # handle the keyboard interruption explicitly
    signal.signal(signal.SIGINT, handler)

    try:
        identify_device()

        class_path = locate_apk_path()

        (code, _, err) = run_adb(
            ["forward", "tcp:%d" % args_in.port, "tcp:%d" % args_in.port])
        print(">>> adb forward tcp:%d " % args_in.port, code)

        print_url()

        args = ["shell",
                class_path,
                "app_process",
                "/",  # unused
                "com.rayworks.droidcast.Main",
                "--port=%d" % args_in.port,
                "--display_id=%d" % args_in.display_id]

        # delay opening the web page
        t = Timer(2, open_browser)
        t.start()

        # event loop starts
        run_adb(args, pipeOutput=False)

    except (Exception) as e:
        print(e)


if __name__ == "__main__":
    automate()
