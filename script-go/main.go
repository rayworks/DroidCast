// Command script-go is a CLI helper for DroidCast. It forwards an ADB TCP
// connection, starts the on-device screenshot service via app_process, and
// opens the live-screen URL in the default browser.
//
// It is a port of script-rs with the same command-line contract:
//
//	script-go <port> [serial]
package main

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

const (
	packageName = "com.rayworks.droidcast"
	mainClass   = "com.rayworks.droidcast.Main"
	// browserDelay is how long to wait for the on-device service to come up
	// before opening the browser.
	browserDelay = 2 * time.Second
	// cleanupTimeout bounds each adb call made during shutdown so a stalled
	// adb server cannot keep the tool from exiting.
	cleanupTimeout = 5 * time.Second
	// childWaitDelay is how long to wait after interrupting app_process
	// before killing it outright.
	childWaitDelay = 3 * time.Second
)

func main() {
	if err := run(os.Args); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// run holds the whole lifecycle so that every exit path, including errors and
// Ctrl-C, goes through the deferred port-forward cleanup.
func run(args []string) error {
	fmt.Printf(">>> args %q\n", args)

	if len(args) != 2 && len(args) != 3 {
		return fmt.Errorf("usage: %s <port> [serial]", filepath.Base(args[0]))
	}

	port, err := parsePort(args[1])
	if err != nil {
		return err
	}

	// Optional device serial number supplied as the third argument.
	serial := ""
	if len(args) == 3 {
		serial = strings.TrimSpace(args[2])
	}

	// ctx is cancelled by SIGINT/SIGTERM. Everything that should stop on
	// Ctrl-C (starting the service, waiting for it, opening the browser)
	// watches this context.
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	stopSignals := setupSignalHandler(ctx, cancel)
	defer stopSignals()

	// adb devices
	if err := checkDevices(ctx, serial); err != nil {
		return err
	}

	// apk path
	fullPath, err := locateApkPath(ctx, serial)
	if err != nil {
		return err
	}

	// forward
	if err := forwardConnection(ctx, port, serial); err != nil {
		return err
	}
	// Remove the forward on every exit path: Ctrl-C, service exit, or a
	// failure below. Cleanup gets its own bounded context because ctx is
	// already cancelled when we get here after a signal.
	defer func() {
		cleanupCtx, cancelCleanup := context.WithTimeout(context.Background(), cleanupTimeout)
		defer cancelCleanup()
		unforwardConnection(cleanupCtx, port, serial)
	}()

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		select {
		case <-time.After(browserDelay):
		case <-ctx.Done():
			return
		}
		fmt.Println("Open the browser on the worker goroutine")
		openBrowser(ctx, port, serial)
	}()

	serviceErr := startServiceAndWait(ctx, port, fullPath, serial)

	// Stop the browser goroutine if it is still waiting, then let it finish.
	cancel()
	wg.Wait()

	if serviceErr != nil {
		return serviceErr
	}
	fmt.Println("About to quit the app")
	return nil
}

// parsePort validates that s is a TCP port in the range 1-65535 and returns
// it in its canonical string form.
func parsePort(s string) (string, error) {
	s = strings.TrimSpace(s)
	n, err := strconv.ParseUint(s, 10, 16)
	if err != nil || n == 0 {
		return "", fmt.Errorf("The port must be a number between 1 and 65535, got %q.", s)
	}
	return strconv.FormatUint(n, 10), nil
}

// serialCheckedCommand returns an adb *exec.Cmd bound to ctx and
// pre-populated with the "-s <serial>" flag when a device serial number is
// provided.
func serialCheckedCommand(ctx context.Context, serial string, args ...string) *exec.Cmd {
	var full []string
	if serial != "" {
		full = append(full, "-s", serial)
	}
	full = append(full, args...)
	return exec.CommandContext(ctx, "adb", full...)
}

// deviceEntry is one row of "adb devices": a serial and its state
// (e.g. "device", "offline", "unauthorized").
type deviceEntry struct {
	serial string
	state  string
}

// listDevices runs "adb devices" and parses each non-header row into a
// deviceEntry.
func listDevices(ctx context.Context) ([]deviceEntry, error) {
	out, err := exec.CommandContext(ctx, "adb", "devices").Output()
	if err != nil {
		return nil, fmt.Errorf("failed to run 'adb devices': %w", err)
	}
	devicesOut := string(out)
	fmt.Printf("\nDevices info : %s\n", devicesOut)
	return parseDevices(devicesOut), nil
}

// parseDevices turns the output of "adb devices" into one deviceEntry per
// device row, skipping the header, blank lines and daemon chatter such as
// "* daemon started successfully".
func parseDevices(devicesOut string) []deviceEntry {
	var devices []deviceEntry
	sc := bufio.NewScanner(strings.NewReader(devicesOut))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "List of devices") || strings.HasPrefix(line, "*") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		devices = append(devices, deviceEntry{serial: fields[0], state: fields[1]})
	}
	return devices
}

// checkDevices verifies that a usable device is attached and that the
// selection is unambiguous: exactly one ready device, or a serial that names
// a ready device when several entries are listed.
func checkDevices(ctx context.Context, serial string) error {
	devices, err := listDevices(ctx)
	if err != nil {
		return err
	}

	ready := 0
	for _, d := range devices {
		if d.state == "device" {
			ready++
		}
	}

	if serial != "" {
		for _, d := range devices {
			if d.serial != serial {
				continue
			}
			if d.state != "device" {
				return fmt.Errorf("Device %s is %s, not ready", serial, d.state)
			}
			return nil
		}
		return fmt.Errorf("Device %s is not connected", serial)
	}

	if ready == 0 {
		return errors.New("Make sure your device is connected")
	}
	if len(devices) > 1 {
		return errors.New("Multiple devices connected, please specify the target device serial number")
	}
	return nil
}

// locateApkPath queries the package manager on the connected device for the
// APK path of the DroidCast package. It returns the "CLASSPATH=<path>" string
// on success, or an error when the package is not found.
func locateApkPath(ctx context.Context, serial string) (string, error) {
	cmd := serialCheckedCommand(ctx, serial, "shell", "pm", "path", packageName)
	var stderr strings.Builder
	cmd.Stderr = &stderr
	out, err := cmd.Output()

	// `pm path` exits non-zero with no output when the package is not
	// installed; that is a "not found" case, not a failure to run adb.
	var exitErr *exec.ExitError
	if err != nil && !errors.As(err, &exitErr) {
		return "", fmt.Errorf("failed to run 'adb shell pm path': %w", err)
	}

	// `pm path` returns a line like `package:/data/app/…/base.apk`; we only
	// need the part after the first colon.
	apkPath := ""
	if _, after, found := strings.Cut(string(out), ":"); found {
		apkPath = strings.TrimSpace(after)
	}

	if apkPath == "" {
		msg := fmt.Sprintf("Apk not found on the device, have you installed %s successfully?", packageName)
		if details := strings.TrimSpace(stderr.String()); details != "" {
			msg += "\nadb reported: " + details
		}
		return "", errors.New(msg)
	}

	fullPath := "CLASSPATH=" + apkPath
	fmt.Printf("Path %s\n", fullPath)

	return fullPath, nil
}

// startServiceAndWait spawns app_process via "adb shell" with the DroidCast
// main class and waits for it to finish. The child's stdio is attached to
// ours so the service log is visible in the terminal.
//
// The child is bound to ctx: if ctx is already cancelled the service is not
// started, and if ctx is cancelled while waiting the child is sent SIGINT
// (then killed after childWaitDelay if it does not exit).
//
// A non-zero exit of the child itself is reported but is not an error, to
// match script-rs. Only a failure to start or wait on the child is returned.
func startServiceAndWait(ctx context.Context, port, fullPath, serial string) error {
	portParam := "--port=" + port
	params := []string{
		"shell",
		strings.TrimSpace(fullPath),
		"app_process",
		"/",
		mainClass,
		strings.TrimSpace(portParam),
	}
	fmt.Printf("Params -> %q\n", params)

	cmd := serialCheckedCommand(ctx, serial, params...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Cancel = func() error { return cmd.Process.Signal(os.Interrupt) }
	cmd.WaitDelay = childWaitDelay

	if err := cmd.Start(); err != nil {
		if ctx.Err() != nil {
			// Interrupted before the service was started; nothing to run.
			return nil
		}
		return fmt.Errorf("failed to spawn app_process: %w", err)
	}

	err := cmd.Wait()
	var exitErr *exec.ExitError
	switch {
	case err == nil, errors.As(err, &exitErr), ctx.Err() != nil:
		// Normal exit, a non-zero child status, or our own interrupt: all
		// are reported status-only like the Rust version does.
		fmt.Printf("status: %s\n", cmd.ProcessState)
		return nil
	default:
		return fmt.Errorf("failed to wait for app_process: %w", err)
	}
}

// forwardConnection forwards the local TCP port to the same port on the
// device using "adb forward".
func forwardConnection(ctx context.Context, port, serial string) error {
	grp := "tcp:" + port
	params := []string{"forward", grp, grp}
	fmt.Printf("Params -> %q\n", params)

	if _, err := serialCheckedCommand(ctx, serial, params...).Output(); err != nil {
		return fmt.Errorf("failed to forward the tcp connection: %w", err)
	}
	return nil
}

// unforwardConnection removes the previously established port forward via
// "adb forward --remove". It never blocks longer than ctx allows.
func unforwardConnection(ctx context.Context, port, serial string) {
	tcp := "tcp:" + port
	cmd := serialCheckedCommand(ctx, serial, "forward", "--remove", tcp)

	err := cmd.Run()
	var exitErr *exec.ExitError
	switch {
	case err == nil, errors.As(err, &exitErr):
		fmt.Printf("adb unforward action status : %s\n", cmd.ProcessState)
	default:
		fmt.Fprintf(os.Stderr, "Failed to run 'adb forward --remove': %v\n", err)
	}
}

// setupSignalHandler cancels the lifecycle context when SIGINT or SIGTERM
// arrives. Cancelling ctx stops the service (or prevents it from starting),
// aborts the pending browser launch, and lets run's deferred cleanup remove
// the port forward. After the first signal the handler is unregistered, so
// a second Ctrl-C falls back to the default action and kills the tool
// immediately if cleanup is stuck.
//
// The returned function unregisters the handler; it is safe to call more
// than once.
func setupSignalHandler(ctx context.Context, cancel context.CancelFunc) (stop func()) {
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, os.Interrupt, syscall.SIGTERM)

	var once sync.Once
	stop = func() { once.Do(func() { signal.Stop(sigs) }) }

	go func() {
		select {
		case sig := <-sigs:
			fmt.Printf("\nReceived signal %v\n", sig)
			stop()
			cancel()
		case <-ctx.Done():
		}
	}()
	return stop
}

// openBrowser retrieves the device's WLAN IP address, prints a shareable URL,
// and opens a local screenshot URL in the default browser.
func openBrowser(ctx context.Context, port, serial string) {
	ipScript := "ip route | awk '/wlan*/{ print $9 }'| tr -d '\\n'"
	ipBytes, err := serialCheckedCommand(ctx, serial, "shell", ipScript).Output()
	if err != nil {
		if ctx.Err() != nil {
			return
		}
		fmt.Fprintf(os.Stderr, "Failed to retrieve device IP address: %v\n", err)
	}
	ip := strings.TrimSpace(string(ipBytes))
	fmt.Printf(">>> Share the url 'http://%s:%s/screenshot' to see the live screen\n", ip, port)

	// Do not open a browser for a service that is being torn down.
	if ctx.Err() != nil {
		return
	}
	url := fmt.Sprintf("http://localhost:%s/screenshot", port)
	if err := launchBrowser(ctx, url); err != nil && ctx.Err() == nil {
		fmt.Println("Failed to open browser")
	}
}

// launchBrowser opens url with the platform's default handler. The launcher
// process is bound to ctx so an in-progress launch is stopped on cancellation.
func launchBrowser(ctx context.Context, url string) error {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.CommandContext(ctx, "open", url)
	case "windows":
		cmd = exec.CommandContext(ctx, "rundll32", "url.dll,FileProtocolHandler", url)
	default:
		cmd = exec.CommandContext(ctx, "xdg-open", url)
	}
	return cmd.Start()
}
