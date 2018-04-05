#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char *adb_command;

static char *apkname;

const char *const fwd_cmd[] = {"forward", "tcp:53516", "tcp:53516"};

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

int main(int argc, char *argv[])
{
    if (argc < 2)
    {
        // e.g /apk/debug/DroidCast-debug-1.0.apk
        error_input();
    }

    char *local = argv[1];
    filter_apk(local);

    int exit_code;

    const char *const push_cmd[] = {"push", local, remote};
    pid_t proc_push = adb_execute(NULL, push_cmd, ARRAY_LEN(push_cmd));
    if (!cmd_simple_wait(proc_push, &exit_code))
    {
        perror("adb push\n");
        exit(-1);
    }

    pid_t proc = adb_execute(NULL, fwd_cmd, ARRAY_LEN(fwd_cmd));
    if (!cmd_simple_wait(proc, &exit_code))
    {
        if (exit_code != -1)
        {
            printf("adb forward : return value %d\n", exit_code);
        }
        else
        {
            printf("adb forward : exited unexpectedly\n");
        }
    }
    else
    {
        printf("adb forward : configured successfully.\n");
    }

    char class_path[108];
    snprintf(class_path, sizeof(class_path), "CLASSPATH=%s%s", remote, apkname);
    printf("> full class path: %s\n", class_path);

    const char *const cmd[] = {
        "shell",
        //"CLASSPATH=/data/local/tmp/DroidCast-debug-1.0.apk",
        class_path,
        "app_process",
        "/", // unused
        "com.rayworks.droidcast.Main"};

    adb_execute(NULL, cmd, ARRAY_LEN(cmd));

    exit(EXIT_SUCCESS);
}