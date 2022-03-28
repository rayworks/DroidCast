use std::env;
use std::process::Command;

use std::{error::Error, thread};
use signal_hook::{iterator::Signals, consts::SIGINT};

use webbrowser;

fn main() {
    setup_signal_handler().expect("Failed to set up signal handler");

    let args: Vec<String> = env::args().collect();
    println!(">>> args {:?}", args);

    if args.len() != 2 {
        println!("usage : prog [port]");
        return;
    }

    let port = args[1].to_string();
    port.trim().parse::<u32>().expect("The port must be a number.");

    // adb devices
    let devices_result = Command::new("adb").arg("devices").output();
    let s = devices_result.unwrap().stdout;
    let devices_out = std::str::from_utf8(&s).unwrap();
    let dev_cnt = devices_out.matches("device").count();
    if dev_cnt < 2 {
        println!("Make sure your device is connected");
        return;
    }

    // apk path
    let params = vec!["shell", "pm", "path", "com.rayworks.droidcast"];
    let path_result = Command::new("adb").args(params).output();
    let path = path_result.unwrap().stdout;

    let classpath = String::from("CLASSPATH=");
    let mut raw_path = std::str::from_utf8(&path).unwrap();
    raw_path = raw_path.split(":").last().unwrap().trim();
    if raw_path.is_empty() {
        println!("Apk not found, have you installed it successfully?");
        return;
    }
    let full_path = classpath.to_string() + &(raw_path.to_string());
    println!("Path {}", full_path);

    // forward
    forward_connection(&port);

    let timer = timer::Timer::new();
    let guard = timer.schedule_with_delay(chrono::Duration::seconds(2), open_browser);

    startup_service_and_wait(&port, full_path);

    // unforward
    unforward_connection(&port);

    drop(guard);
    println!("About to quit the app");
}

fn startup_service_and_wait(port: &String, full_path: String) {
    // app_process
    let port_param = String::from("--port=") + &port;
    let params = vec![
        "shell",
        full_path.trim(),
        "app_process",
        "/",
        "com.rayworks.droidcast.Main",
        port_param.as_str().trim(),
    ];
    println!("Params -> {:?}", params);

    let result = Command::new("adb").args(params)
        .spawn()
        .unwrap()
        .wait_with_output()
        .expect("Failed to wait for a Child Process");

    println!("status: {}", result.status);
}

fn forward_connection(port: &String) {
    let grp = String::from("tcp:") + &port;
    let params_fwd = vec!["forward", &grp.trim(), &grp.trim()];
    println!("Params -> {:?}", params_fwd);

    Command::new("adb").args(params_fwd).output().expect("Failed to forward the tcp connection");
}

fn unforward_connection(port: &String) {
    let tcp = format!("tcp:{}", &port);
    let params_fwd = vec!["forward", "--remove", &tcp];

    let status = Command::new("adb").args(params_fwd).output().unwrap().status;
    println!("adb unforward action status : {:?}", status);
}

fn setup_signal_handler() -> Result<(), Box<dyn Error>> {
    let mut signals = Signals::new(&[SIGINT])?;

    thread::spawn(move || {
        for sig in signals.forever() {
            println!("\nReceived signal {:?}", sig);
        }
    });

    Ok(())
}

fn open_browser() {
    let args: Vec<String> = env::args().collect();

    let ip_param = vec!["shell", "ip route | awk '/wlan*/{ print $9 }'| tr -d '\n'"];
    let ip = Command::new("adb").args(ip_param).output().unwrap().stdout;
    let ip = std::str::from_utf8(&ip).unwrap().to_string();
    println!(">>> Share the url 'http://{}:{}/screenshot' to see the live screen", ip, args[1]);

    let url = format!("http://localhost:{}/screenshot", args[1]);
    if webbrowser::open(&url).is_err() {
        println!("Failed to open browser");
    }
}
