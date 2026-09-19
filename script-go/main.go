// Command script-go is a CLI helper for DroidCast. It forwards an ADB TCP
// connection, starts the on-device screenshot service via app_process, and
// opens the live-screen URL in the default browser.
//
// It is a port of script-rs with the same behaviour and command-line contract:
//
//	script-go <port> [serial]
package main

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
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
)

func main() {
	args := os.Args
	fmt.Printf(">>> args %q\n", args)

	if len(args) != 2 && len(args) != 3 {
		fmt.Println("usage : prog port [serial number]")
		return
	}

	port := strings.TrimSpace(args[1])
	if _, err := strconv.ParseUint(port, 10, 32); err != nil {
		fmt.Fprintln(os.Stderr, "The port must be a number.")
		os.Exit(1)
	}

	// Optional device serial number supplied as the third argument.
	serial := ""
	if len(args) == 3 {
		serial = args[2]
	}

	// adb devices
	devCnt, err := countConnectedDevices()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	if devCnt < 2 {
		fmt.Println("Make sure your device is connected")
		return
	} else if devCnt > 2 && serial == "" {
		fmt.Println("Multiple devices connected, please specify the target device serial number")
		return
	}

	// apk path
	fullPath, err := locateApkPath(serial)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	// forward
	if err := forwardConnection(port, serial); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	// Register the SIGINT handler after the forward is established so that
	// Ctrl-C always tears down the port forward before the process exits.
	// The handler also interrupts the app_process child so the tool never
	// blocks waiting on it when the signal was delivered only to us.
	var svc service
	stopSignals, signalDone := setupSignalHandler(port, serial, svc.interrupt)

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		time.Sleep(browserDelay)

		fmt.Println("Open the browser on the worker goroutine")
		openBrowser(port, serial)
	}()

	svc.startAndWait(port, fullPath, serial)

	// On a normal (non-SIGINT) exit, stop listening for signals so the handler
	// goroutine can return without waiting for another signal.
	stopSignals()
	<-signalDone

	wg.Wait()
	fmt.Println("About to quit the app")
}

// serialCheckedCommand returns an adb *exec.Cmd pre-populated with the
// "-s <serial>" flag when a device serial number is provided.
func serialCheckedCommand(serial string, args ...string) *exec.Cmd {
	var full []string
	if serial != "" {
		full = append(full, "-s", serial)
	}
	full = append(full, args...)
	return exec.Command("adb", full...)
}

// countConnectedDevices runs "adb devices" and returns the number of lines
// that contain the word "device" (including the header), which is a proxy for
// the number of recognised entries.
func countConnectedDevices() (int, error) {
	out, err := exec.Command("adb", "devices").Output()
	if err != nil {
		return 0, fmt.Errorf("failed to run 'adb devices': %w", err)
	}
	devicesOut := string(out)

	fmt.Printf("\nDevices info : %s\n", devicesOut)
	return strings.Count(devicesOut, "device"), nil
}

// locateApkPath queries the package manager on the connected device for the
// APK path of the DroidCast package. It returns the "CLASSPATH=<path>" string
// on success, or an error when the package is not found.
func locateApkPath(serial string) (string, error) {
	cmd := serialCheckedCommand(serial, "shell", "pm", "path", packageName)
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

// service tracks the "adb shell ... app_process" child so that a signal
// handler can interrupt it from another goroutine.
type service struct {
	mu  sync.Mutex
	cmd *exec.Cmd
}

// interrupt sends SIGINT to the running app_process child, if any. It is
// safe to call before the child has started or after it has exited.
func (s *service) interrupt() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cmd == nil || s.cmd.Process == nil {
		return
	}
	if err := s.cmd.Process.Signal(os.Interrupt); err != nil && !errors.Is(err, os.ErrProcessDone) {
		fmt.Fprintf(os.Stderr, "Failed to interrupt app_process: %v\n", err)
	}
}

// startAndWait spawns app_process via "adb shell" with the DroidCast main
// class and waits for the process to finish. The child's stdio is attached to
// ours so the service log is visible in the terminal.
func (s *service) startAndWait(port, fullPath, serial string) {
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

	cmd := serialCheckedCommand(serial, params...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	s.mu.Lock()
	err := cmd.Start()
	if err == nil {
		s.cmd = cmd
	}
	s.mu.Unlock()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to spawn app_process: %v\n", err)
		return
	}

	var exitErr *exec.ExitError
	err = cmd.Wait()
	switch {
	case err == nil:
		fmt.Printf("status: %s\n", cmd.ProcessState)
	case errors.As(err, &exitErr):
		// A non-zero exit (e.g. the child was interrupted by Ctrl-C) is normal
		// here; report the status like the Rust version does.
		fmt.Printf("status: %s\n", exitErr.ProcessState)
	default:
		fmt.Fprintf(os.Stderr, "Failed to wait for app_process: %v\n", err)
	}
}

// forwardConnection forwards the local TCP port to the same port on the
// device using "adb forward".
func forwardConnection(port, serial string) error {
	grp := "tcp:" + port
	params := []string{"forward", grp, grp}
	fmt.Printf("Params -> %q\n", params)

	if _, err := serialCheckedCommand(serial, params...).Output(); err != nil {
		return fmt.Errorf("failed to forward the tcp connection: %w", err)
	}
	return nil
}

// unforwardConnection removes the previously established port forward via
// "adb forward --remove".
func unforwardConnection(port, serial string) {
	tcp := "tcp:" + port
	cmd := serialCheckedCommand(serial, "forward", "--remove", tcp)

	err := cmd.Run()
	var exitErr *exec.ExitError
	switch {
	case err == nil:
		fmt.Printf("adb unforward action status : %s\n", cmd.ProcessState)
	case errors.As(err, &exitErr):
		fmt.Printf("adb unforward action status : %s\n", exitErr.ProcessState)
	default:
		fmt.Fprintf(os.Stderr, "Failed to run 'adb forward --remove': %v\n", err)
	}
}

// setupSignalHandler installs a SIGINT/SIGTERM handler that removes the
// active port forward when the user presses Ctrl-C and then calls onSignal
// (used to interrupt the app_process child). It returns a stop function that
// unregisters the handler (allowing the goroutine to return on a clean exit)
// and a channel that is closed once the handler goroutine has finished.
func setupSignalHandler(port, serial string, onSignal func()) (stop func(), done <-chan struct{}) {
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, os.Interrupt, syscall.SIGTERM)

	quit := make(chan struct{})
	finished := make(chan struct{})

	go func() {
		defer close(finished)
		select {
		case sig := <-sigs:
			fmt.Printf("\nReceived signal %v\n", sig)
			unforwardConnection(port, serial)
			onSignal()
		case <-quit:
		}
	}()

	var once sync.Once
	stop = func() {
		once.Do(func() {
			signal.Stop(sigs)
			close(quit)
		})
	}
	return stop, finished
}

// openBrowser retrieves the device's WLAN IP address, prints a shareable URL,
// and opens a local screenshot URL in the default browser.
func openBrowser(port, serial string) {
	ipScript := "ip route | awk '/wlan*/{ print $9 }'| tr -d '\\n'"
	ipBytes, err := serialCheckedCommand(serial, "shell", ipScript).Output()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to retrieve device IP address: %v\n", err)
	}
	ip := strings.TrimSpace(string(ipBytes))
	fmt.Printf(">>> Share the url 'http://%s:%s/screenshot' to see the live screen\n", ip, port)

	url := fmt.Sprintf("http://localhost:%s/screenshot", port)
	if err := launchBrowser(url); err != nil {
		fmt.Println("Failed to open browser")
	}
}

// launchBrowser opens url with the platform's default handler.
func launchBrowser(url string) error {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	return cmd.Start()
}
