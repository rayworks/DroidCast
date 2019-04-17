//
// Created by Sean Zhou on 11/13/18.
//

#include <assert.h>
#include <string.h>

#include "../utils/string_util.h"

static void test_filtering(void) {
    char* result = "package:/data/app/com.rayworks.droidcast-Tb1-e8DHFvuQ1wI6_MlLww==/base.apk";
    char * pstr = filter_apk_path(result);
    assert(!strcmp(pstr, "/data/app/com.rayworks.droidcast-Tb1-e8DHFvuQ1wI6_MlLww==/base.apk"));
}

static void test_filtering_kitkat(void) {
    char* result = "package:/data/app/com.rayworks.droidcast-2.apk";
    char * pstr = filter_apk_path(result);
    assert(!strcmp(pstr, "/data/app/com.rayworks.droidcast-2.apk"));
}

int main(int argc, char const *argv[])
{
    test_filtering();
    test_filtering_kitkat();

    return 0;
}
