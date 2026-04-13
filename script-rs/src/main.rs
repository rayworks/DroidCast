use std::env;
use std::process::Command;

use std::{error::Error, thread};
use std::time::Duration;
use signal_hook::{iterator::Signals, consts::SIGINT};

fn main() {
    setup_signal_handler().expect("Failed to set up signal handler");

    let args: Vec<String> = env::args().collect();
    println!(">>> args {:?}", args);

    if args.len() != 2 && args.len() != 3 {
        println!("usage : prog port [serial number]");
        return;
    }

    let port = args[1].trim();
    port.parse::<u32>().expect("The port must be a number.");

    // Optional device serial number supplied as the third argument.
    let serial: Option<&str> = args.get(2).map(|s| s.as_str());

    // adb devices
    let dev_cnt = count_connected_devices();
    if dev_cnt < 2 {
        println!("Make sure your device is connected");
        return;
    } else if dev_cnt > 2 && serial.is_none() {
        println!("Multiple devices connected, please specify the target device serial number");
        return;
    }

    // apk path
    let full_path = match locate_apk_path(serial) {
        Some(p) => p,
        None => return,
    };

    // forward
    forward_connection(port, serial);

    // Clone owned values for the worker thread.
    let port_owned = port.to_string();
    let serial_owned = serial.map(str::to_string);
    let handle = thread::spawn(move || {
        thread::sleep(Duration::from_secs(2));

        println!("Open the browser on the worker thread");
        open_browser(&port_owned, serial_owned.as_deref());
    });

    startup_service_and_wait(port, full_path, serial);

    // unforward
    unforward_connection(port, serial);

    handle.join().expect("Browser thread panicked");
    println!("About to quit the app");
}

/// Returns an `adb` [`Command`] pre-populated with the `-s <serial>` flag when
/// a device serial number is provided.
fn serial_checked_command(serial: Option<&str>) -> Command {
    let mut cmd = Command::new("adb");
    if let Some(s) = serial {
        cmd.args(["-s", s]);
    }
    cmd
}

/// Runs `adb devices` and returns the number of lines that contain the word
/// `"device"` (including the header), which is a proxy for the number of
/// recognised entries.
fn count_connected_devices() -> usize {
    let output = Command::new("adb")
        .arg("devices")
        .output()
        .expect("Failed to run 'adb devices'");
    let devices_out =
        std::str::from_utf8(&output.stdout).expect("'adb devices' output is not valid UTF-8");

    println!("\nDevices info : {}", devices_out);
    devices_out.matches("device").count()
}

/// Queries the package manager on the connected device for the APK path of
/// `com.rayworks.droidcast`.  Returns `Some(classpath_string)` on success, or
/// `None` (after printing an error) when the package is not found.
fn locate_apk_path(serial: Option<&str>) -> Option<String> {
    let params = ["shell", "pm", "path", "com.rayworks.droidcast"];
    let output = serial_checked_command(serial)
        .args(params)
        .output()
        .expect("Failed to run 'pm path'");

    let raw =
        std::str::from_utf8(&output.stdout).expect("'pm path' output is not valid UTF-8");

    // `pm path` returns a line like `package:/data/app/…/base.apk`; we only
    // need the part after the first colon.
    let apk_path = raw
        .split_once(':')
        .map(|(_, after)| after.trim())
        .unwrap_or("");

    if apk_path.is_empty() {
        eprintln!("Apk not found, have you installed it successfully?");
        return None;
    }

    let full_path = format!("CLASSPATH={}", apk_path);
    println!("Path {}", full_path);

    Some(full_path)
}

/// Spawns `app_process` via `adb shell` with the DroidCast main class and
/// waits for the process to finish.
fn startup_service_and_wait(port: &str, full_path: String, serial: Option<&str>) {
    let port_param = format!("--port={}", port);
    let params = [
        "shell",
        full_path.trim(),
        "app_process",
        "/",
        "com.rayworks.droidcast.Main",
        port_param.trim(),
    ];
    println!("Params -> {:?}", params);

    let result = serial_checked_command(serial)
        .args(params)
        .spawn()
        .expect("Failed to spawn app_process")
        .wait_with_output()
        .expect("Failed to wait for app_process");

    println!("status: {}", result.status);
}

/// Forwards the local TCP port to the same port on the device using `adb forward`.
fn forward_connection(port: &str, serial: Option<&str>) {
    let grp = format!("tcp:{}", port);
    let params_fwd = ["forward", grp.trim(), grp.trim()];
    println!("Params -> {:?}", params_fwd);

    serial_checked_command(serial)
        .args(params_fwd)
        .output()
        .expect("Failed to forward the tcp connection");
}

/// Removes the previously established port forward via `adb forward --remove`.
fn unforward_connection(port: &str, serial: Option<&str>) {
    let tcp = format!("tcp:{}", port);
    let params_fwd = ["forward", "--remove", &tcp];

    let status = serial_checked_command(serial)
        .args(params_fwd)
        .output()
        .expect("Failed to run 'adb forward --remove'")
        .status;
    println!("adb unforward action status : {:?}", status);
}

/// Installs a `SIGINT` handler that prints a message when the user presses
/// Ctrl-C.  The signal is handled on a dedicated background thread so that the
/// main thread is not interrupted.
fn setup_signal_handler() -> Result<(), Box<dyn Error>> {
    let mut signals = Signals::new([SIGINT])?;

    thread::spawn(move || {
        for sig in signals.forever() {
            println!("\nReceived signal {:?}", sig);
        }
    });

    Ok(())
}

/// Retrieves the device's WLAN IP address, prints a shareable URL, and opens
/// a local screenshot URL in the default browser.
fn open_browser(port: &str, serial: Option<&str>) {
    let ip_param = ["shell", "ip route | awk '/wlan*/{ print $9 }'| tr -d '\n'"];
    let ip_bytes = serial_checked_command(serial)
        .args(ip_param)
        .output()
        .expect("Failed to retrieve device IP address")
        .stdout;
    let ip = std::str::from_utf8(&ip_bytes)
        .expect("Device IP output is not valid UTF-8");
    println!(
        ">>> Share the url 'http://{}:{}/screenshot' to see the live screen",
        ip, port
    );

    let url = format!("http://localhost:{}/screenshot", port);
    if webbrowser::open(&url).is_err() {
        println!("Failed to open browser");
    }
}
