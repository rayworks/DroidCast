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
    description='Automation script to connect the current Android device wireless')
parser.add_argument('-s', '--serial', dest='device_serial',
                    help='Device serial number (adb -s option)')
parser.add_argument(
    '-p',
    '--port',
    dest='port',
    nargs='?',
    const=5555,
    type=int,
    default=5555,
    help='Port number to be listening on via TCP, by default it\'s 5555')
args_in = parser.parse_args()


def run_adb(args, pipeOutput=True):
    if (args_in.device_serial):
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


def identify_device():
    (rc, out, _) = run_adb(["devices"])
    if (rc):
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

        if (deviceCnt > 1 and (not device_serial_no)):
            raise RuntimeError(
                "Please specify the serial number of target device you want to use ('-s serial_number').")


def start_tcp_ip(ip):
    print(">>> listening on TCP on %d" % args_in.port)
    run_adb(['tcpip', args_in.port])

    print(">>> adb connect")
    (rc, out, _) = run_adb(['connect', ip])
    if rc == 0:
        print("Device connected from %s" % ip)


def retrieve_ip():
    # ip route:
    # e.g. 192.168.0.0/24 dev wlan0 proto kernel scope link src 192.168.0.125
    (rc, out, _) = run_adb(
        ["shell", "ip route | awk '/wlan*/{ print $9 }'| tr -d '\n'"])
    print("device ip : %s" % out)
    return out


def handler(signum, frame):
    print('\n>>> Signal caught: ', signum)
    if ip:
        (_, out, err) = run_adb(['disconnect', ip])
    print(">>> Device disconnected from %d" % ip)


ip = ""


def automate():
    # handle the keyboard interruption explicitly
    signal.signal(signal.SIGINT, handler)

    try:
        identify_device()

        ip = retrieve_ip()
        start_tcp_ip(ip)

    except Exception as e:
        print(e)


if __name__ == "__main__":
    automate()
