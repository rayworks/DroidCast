#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <errno.h>

#include "./error_printer.h"

static const char *adb_command;

static char *apkname;

static const char *const fwd_cmd[] = {"forward", "tcp:53516", "tcp:53516"};

static const char *const un_fwd_cmd[] = {"forward", "--remove", "tcp:53516"};

static const char *remote = "/data/local/tmp/";

/* methods taken and modified from https://github.com/Genymobile/scrcpy/ */

#define ARRAY_LEN(a) (sizeof(a) / sizeof(a[0]))

#define BUF_SIZE 1024*2

// NB: Give that the target apk/dex file has been pushed into '/data/local/tmp/' on the device, it's
// still possible to get an error about "could not find class 'com.rayworks.droidcast.Main'". so we
// retrieve the apk source path via 'adb shell pm path your-pkg-name'.
static const char *apk_src_path = NULL;

static void sig_pipe(int id)
{
    printf("SIGPIPE caught\n");
    exit(1);
}

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
        // reset
        signal(SIGCHLD, SIG_DFL);

        int status;
        pid_t child_proc;
        if ((child_proc = waitpid(-1, &status, 0)) > 0)
        {
            printf("handler : Reaped child %ld\n", (long)child_proc);
            if (WIFEXITED(status))
            {
                printf("child exited, with status : %d\n", WEXITSTATUS(status));
            }
            else if (WIFSIGNALED(status))
            { // not reached ?!
                printf("child killed by signal %d (%s)\n", WTERMSIG(status), strsignal(WTERMSIG(status)));
            }
        }
        else
        {

            perror("waitpid error");
            exit(EXIT_FAILURE);
        }

        count++;
        printf("Caught SIGCHLD : %d time(s) \n", count);

        pid_t proc = adb_execute(NULL, un_fwd_cmd, ARRAY_LEN(un_fwd_cmd));
        wait_for_child_process(proc, "adb undo forward");
    }
}

// Use a pipe to communicate between a parent and child process (via Android Package Manager) for
// retrieving the actual installed apk path.
void retrieve_src_apk_path()
{
    int fd[2];
    pid_t pid;

    char line[MAXLINE];

    if (signal(SIGPIPE, sig_pipe) == SIG_ERR) {
        err_sys("signal error");
    }

    if (pipe(fd) < 0)
        err_sys("pipe error");

    if ((pid = fork()) < 0) {
        err_sys("fork error");
    } else if (pid > 0) { //parent
        close(fd[1]); // out closed for parent

        char buffer[BUF_SIZE];

        char result[BUF_SIZE];
        memset(result, 0, BUF_SIZE);

        int readCnt = 0;
        while (1) {
            ssize_t count = read(fd[0], buffer, sizeof (buffer));
            printf("bytes %ld read from pipe\n", count);

            if (count == -1) {
                if (errno == EINTR) {
                    continue;
                } else {
                    perror("read");
                    exit(1);
                }
            } else if (count == 0) {
                break;
            } else {
                // NB: the read content could be discontinued, so gather all the content first
                char* ptr = result;

                strncpy(ptr + readCnt, buffer, count);
                readCnt += count;
            }
        }

        if (readCnt > 0) {
            // format like:
            // package:/data/app/com.rayworks.droidcast-Tb1-e8DHFvuQ1wI6_MlLww==/base.apk

            char* pstart = strchr(result, ':');
            pstart++;

            const char* pBaseApk = "base.apk";
            char* pend = strstr(result, pBaseApk);
            pend += strlen(pBaseApk) - 1;

            printf("filter string : %s", result);

            char* pstr = (char*) malloc((pend - pstart + 2) * sizeof (char));
            memcpy(pstr, pstart, pend - pstart + 1);
            pstr[pend - pstart + 2] = 0; // terminator added

            apk_src_path = pstr;

            printf("Target path is : %s\n", apk_src_path);
        }

        close(fd[0]);

        wait(NULL);

    } else {
        if (fd[1] != STDOUT_FILENO) {
            if (dup2(fd[1], STDOUT_FILENO) != STDOUT_FILENO) {
                err_sys("dup2 error to stdout");
            }
            close(fd[1]);
            close(fd[0]);
        }

        const char *const cmd[] = {
                    "shell",
                    "pm",
                    "path",
                    "com.rayworks.droidcast"
                };
         pid_t proc = adb_execute(NULL, cmd, ARRAY_LEN(cmd));
         wait(NULL);

         printf("child proc existing\n");

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

    retrieve_src_apk_path();

    char class_path[256];
    if(apk_src_path) {
        snprintf(class_path, sizeof(class_path), "CLASSPATH=%s", apk_src_path);
    }else {
        snprintf(class_path, sizeof(class_path), "CLASSPATH=%s%s", remote, apkname);
    }
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

    int status;
    pid_t childPid;
    while ((childPid = waitpid(-1, &status, 0)) > 0)
    {
        continue;
    }

    if (childPid == -1)
    {
        if (errno == ECHILD)
        {
            printf("No more child process to be waiting, main process exiting now.\n");
        }
        else
        {
            perror("waitpid");
            exit(EXIT_FAILURE);
        }
    }

    exit(EXIT_SUCCESS);
}