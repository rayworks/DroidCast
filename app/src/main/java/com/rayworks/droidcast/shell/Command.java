package com.rayworks.droidcast.shell;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;

/***
 * Code modified and taken from
 * <a href="https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/Command.java">Scrcpy</a>)
 */
public final class Command {
    private Command() {
        // not instantiable
    }

    public static void exec(String... cmd) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(cmd);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command " + Arrays.toString(cmd) + " returned with value " + exitCode);
        }
    }

    public static String execReadLine(String... cmd) throws IOException, InterruptedException {
        String result = null;
        Process process = Runtime.getRuntime().exec(cmd);
        Scanner scanner = new Scanner(process.getInputStream());
        if (scanner.hasNextLine()) {
            result = scanner.nextLine();
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command " + Arrays.toString(cmd) + " returned with value " + exitCode);
        }
        return result;
    }

    public static String execReadOutput(String... cmd) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(cmd);
        String output = toString(process.getInputStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command " + Arrays.toString(cmd) + " returned with value " + exitCode);
        }
        return output;
    }

    private static String toString(InputStream inputStream) {
        StringBuilder builder = new StringBuilder();
        Scanner scanner = new Scanner(inputStream);
        while (scanner.hasNextLine()) {
            builder.append(scanner.nextLine()).append('\n');
        }
        return builder.toString();
    }
}
