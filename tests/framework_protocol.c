#include "framework_protocol.h"
#include <assert.h>
int main(void) {
    LumaFrameworkSnapshot s={0};
    assert(!luma_framework_parse("OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 0\n",&s));
    assert(!luma_framework_parse("OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 1 1\n",&s) && s.user_hold==1);
    const char *bad[]={"ERR\n","OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09\n",
      "OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 0 extra\n",
      "OK 2 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 0\n",
      "OK 1 0 62 2 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 0\n",
      "OK 1 nan 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 0\n",
      "OK 1 0 62 1 0 1200 .5 .374 .09 .09 -1 -1 .09 0 0\n",
      "OK 1 0 62 1 0 1200 .0003 .374 .09 .09 2 -1 .09 0 0\n",
      "OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 nan 0 0\n",
      "OK 1 0 62 1 0 20000 .0003 .374 .09 .09 -1 -1 .09 0 0\n",
      "OK 1 0 62 1 0 1200 .0003 .374 .09 .09 -1 -1 .09 0 2\n"};
    for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)assert(luma_framework_parse(bad[i],&s));
    char buffer[256];const char *root="/data/local/tmp/luma-framework-core.TEST";
    assert(luma_framework_map_path(root,"/data/local/tmp/luma_curve.conf",buffer,sizeof(buffer))==buffer);
    assert(!strcmp(buffer,"/data/local/tmp/luma-framework-core.TEST/luma_curve.conf"));
    const char *node="/sys/class/backlight/panel0-backlight/brightness";
    assert(luma_framework_map_path(root,node,buffer,sizeof(buffer))==node);
    assert(!luma_framework_map_path(root,"/data/local/tmp/luma_curve../oops",buffer,sizeof(buffer)));
    assert(!luma_framework_map_path(root,"/data/local/tmp/luma_curve.pid",buffer,2));
    puts("framework protocol: 13 responses and 4 isolation cases PASS");return 0;
}
