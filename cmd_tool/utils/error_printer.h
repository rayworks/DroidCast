//
// Created by Sean Zhou on 9/5/18.
//

#ifndef DROIDCAST_ERROR_PRINTER_H
#define DROIDCAST_ERROR_PRINTER_H

#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <stdlib.h>
#include <signal.h>

#include <errno.h>  /* for definition of errno */
#include <stdarg.h>  /* ISO C variable aruments */
#include <string.h>
#include <stddef.h>

#define MAXLINE 1024

char* strerror(int error);
static void err_doit(int errnoflag, int error, const char *fmt, va_list ap);
void err_msg(const char *fmt, ...);
void err_sys(const char *fmt, ...);

#endif //DROIDCAST_ERROR_PRINTER_H
