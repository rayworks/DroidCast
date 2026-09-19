# script-go

A Go port of [`script-rs`](../script-rs): a CLI helper for DroidCast that
forwards the ADB TCP port, starts the on-device screenshot service via
`app_process`, and opens the live-screen URL in your default browser.
It uses only the Go standard library.

## Requirements

*   Go 1.22+
*   `adb` on your `PATH`
*   The DroidCast apk installed on the device

## Build

    cd script-go
    go build -o script-go .

## Usage

    ./script-go <port> [serial]

*   `port` – the TCP port used for both the local forward and the device service, e.g. `53516`
*   `serial` – optional device serial (required when more than one device is attached)

Example:

    ./script-go 53516
    ./script-go 53516 RFCY813HE3Z

The tool prints a shareable `http://<device-wlan-ip>:<port>/screenshot` URL,
opens `http://localhost:<port>/screenshot` in the browser after two seconds,
and keeps running until the on-device service exits. Press Ctrl-C to stop;
the port forward is removed and the `app_process` child is interrupted before
the tool exits.
