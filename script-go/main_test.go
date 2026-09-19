package main

import (
	"reflect"
	"testing"
)

func TestParsePort(t *testing.T) {
	valid := map[string]string{"1": "1", " 53516 ": "53516", "65535": "65535", "007": "7"}
	for in, want := range valid {
		got, err := parsePort(in)
		if err != nil || got != want {
			t.Errorf("parsePort(%q) = %q, %v; want %q, nil", in, got, err, want)
		}
	}
	for _, in := range []string{"", "abc", "-1", "0", "65536", "1.5", "12a"} {
		if _, err := parsePort(in); err == nil {
			t.Errorf("parsePort(%q) succeeded; want error", in)
		}
	}
}

func TestParseDevices(t *testing.T) {
	out := "* daemon not running; starting now at tcp:5037\n" +
		"* daemon started successfully\n" +
		"List of devices attached\n" +
		"RFCY813HE3Z\tdevice\n" +
		"emulator-5554\toffline\n" +
		"192.168.1.5:5555\tunauthorized\n" +
		"\n"
	want := []deviceEntry{
		{"RFCY813HE3Z", "device"},
		{"emulator-5554", "offline"},
		{"192.168.1.5:5555", "unauthorized"},
	}
	if got := parseDevices(out); !reflect.DeepEqual(got, want) {
		t.Errorf("parseDevices = %+v; want %+v", got, want)
	}
	if got := parseDevices("List of devices attached\n\n"); len(got) != 0 {
		t.Errorf("parseDevices(empty) = %+v; want none", got)
	}
}
