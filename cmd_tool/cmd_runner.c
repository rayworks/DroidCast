#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>

static const char *adb_command;

static char *apkname;

static const char *const fwd_cmd[] = {"forward", "tcp:53516", "tcp:53516"};

static const char *const un_fwd_cmd[] = {"forward", "--remove", "tcp:53516"};

static const char *remote = "/data/local/tmp/";

/* methods taken and modified from https://github.com/Genymobile/scrcpy/ */

#define ARRAY_LEN(a) (sizeof(a) / sizeof(a[0]))

static inline const char *get_adb_command()
{
    if (!adb_command)
    {
        adb_command = getenv("ADB");
        if (!adb_command)
            adb_command = "adb";
    }
    return adb_command;
}

pid_t cmd_execute(const char *path, const char *const argv[])
{
    pid_t pid = fork();
    if (pid == -1)
    {
        perror("fork");
        return -1;
    }
    if (pid == 0)
    {
        execvp(path, (char *const *)argv);
        perror("exec");
        _exit(1);
    }
    return pid;
}

pid_t adb_execute(const char *serial, const char *const adb_cmd[], int len)
{
    const char *cmd[len + 4];
    int i;
    cmd[0] = get_adb_command();
    if (serial)
    {
        cmd[1] = "-s";
        cmd[2] = serial;
        i = 3;
    }
    else
    {
        i = 1;
    }

    memcpy(&cmd[i], adb_cmd, len * sizeof(const char *));
    cmd[len + i] = NULL;
    return cmd_execute(cmd[0], cmd);
}

int cmd_simple_wait(pid_t pid, int *exit_code)
{
    int status;
    int code;
    if (waitpid(pid, &status, 0) == -1 || !WIFEXITED(status))
    {
        // cannot wait, or exited unexpectedly, probably by a signal
        code = -1;
    }
    else
    {
        code = WEXITSTATUS(status);
    }
    if (exit_code)
    {
        *exit_code = code;
    }
    return !code;
}

void error_input()
{
    printf("Usage : ./prog [FULLPATH-TO-YOUR-APK-FILE]\n");
    exit(-1);
}

void filter_apk(char *full_path)
{
    char *segment = strrchr(full_path, '/');

    if (segment)
    {
        char *postfix = strstr(segment, ".apk");
        if (!postfix)
        {
            error_input();
        }
        else
        {
            char *ptr = segment;
            int length = (int)strlen(segment);
            apkname = (char *)malloc(sizeof(char) * length);
            memcpy(apkname, ++ptr, length - 1);

            *(apkname + (length - 1)) = '\0';

            printf("> final apk name : '%s'\n", apkname);
        }
    }
    else
    {
        error_input();
    }
}

void wait_for_child_process(pid_t proc, char *p_cmd)
{
    int exit_code;
    if (!cmd_simple_wait(proc, &exit_code))
    {
        if (exit_code != -1)
        {
            printf("Cmd \'%s\' : return value %d\n", p_cmd, exit_code);
        }
        else
        {
            printf("Cmd \'%s\' : exited unexpectedly\n", p_cmd);
        }

        perror(p_cmd);
        exit(-1);
    }
    else
    {
        printf("Cmd \'%s\' executed successfully\n", p_cmd);
    }
}

static void handler(int sig)
{
    static int count = 0;

    /*
        UNSAFE: Non-async-signal-safe functions used.
     */
    if (sig == SIGCHLD)
    {
        count++;
        printf("Caught SIGCHLD : %d time(s) \n", count);

        pid_t proc = adb_execute(NULL, un_fwd_cmd, ARRAY_LEN(un_fwd_cmd));
        wait_for_child_process(proc, "adb undo forward");

        exit(EXIT_SUCCESS);
    }
}

int main(int argc, char *argv[])
{
    if (argc < 2)
    {
        // e.g /apk/debug/DroidCast-debug-1.0.apk
        error_input();
    }

    char *local = argv[1];
    filter_apk(local);

    const char *const push_cmd[] = {"push", local, remote};
    pid_t proc_push = adb_execute(NULL, push_cmd, ARRAY_LEN(push_cmd));
    wait_for_child_process(proc_push, "adb push");

    pid_t proc = adb_execute(NULL, fwd_cmd, ARRAY_LEN(fwd_cmd));
    wait_for_child_process(proc, "adb forward");

    char class_path[108];
    snprintf(class_path, sizeof(class_path), "CLASSPATH=%s%s", remote, apkname);
    printf("> full class path: %s\n", class_path);

    // setup the handler for monitoring the core child process quitting
    signal(SIGCHLD, handler);

    const char *const cmd[] = {
        "shell",
        class_path,
        "app_process",
        "/", // unused
        "com.rayworks.droidcast.Main"};

    proc = adb_execute(NULL, cmd, ARRAY_LEN(cmd));
    wait_for_child_process(proc, "adb cmd runner");

    exit(EXIT_SUCCESS);
}