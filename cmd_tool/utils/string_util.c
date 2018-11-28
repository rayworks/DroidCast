//
// Created by Sean Zhou on 11/13/18.
//

#include "string_util.h"

char* filter_apk_path(char* result) {
    // format like:
    // package:/data/app/com.rayworks.droidcast-Tb1-e8DHFvuQ1wI6_MlLww==/base.apk

    char* pstart = strchr(result, ':');
    if(!pstart)
        return NULL;
    
    pstart++;

    const char* pBaseApk = "base.apk";
    char* pend = strstr(result, pBaseApk);
    pend += strlen(pBaseApk) - 1;

    printf("filter string : %s", result);

    char* pstr = (char*) malloc((pend - pstart + 2) * sizeof (char));
    pstr[pend - pstart + 1] = 0; // terminator added
    memcpy(pstr, pstart, pend - pstart + 1);

    return pstr;
}
