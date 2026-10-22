#ifndef HYPERLUX_FOLD_PANEL_H
#define HYPERLUX_FOLD_PANEL_H
#include <dirent.h>
/* Matches the verified DSI connector/physical-port ABI; never illuminates an off panel. */
static inline int fold_attribute(const char *root,const char *name,const char *attribute,const char *expected){
 char path[1024],value[64],tail;int n=snprintf(path,sizeof(path),"%s/class/drm/%s/%s",root,name,attribute);
 if(n<=0||n>=(int)sizeof(path))return 0;FILE *f=fopen(path,"r");if(!f)return 0;
 int count=fscanf(f,"%63s %c",value,&tail);fclose(f);return count==1&&!strcmp(value,expected);
}
static inline int fold_panel_active_port(const char *root){
 char path[1024];snprintf(path,sizeof(path),"%s/class/drm",root);DIR *dir=opendir(path);if(!dir)return -1;
 int active=0,selected=-1,entries=0;struct dirent *e;
 while((e=readdir(dir))){if(++entries>66){active=-1;break;}int card,index;char tail;
  if(sscanf(e->d_name,"card%d-DSI-%d%c",&card,&index,&tail)!=2||card<0||index<1)continue;
  if(fold_attribute(root,e->d_name,"status","connected")&&fold_attribute(root,e->d_name,"enabled","enabled")&&fold_attribute(root,e->d_name,"dpms","On")){active++;selected=index-1;}
 }
 closedir(dir);return active==1&&selected>=0&&selected<=7?selected:-1;
}
static inline int fold_panel_live(const char *root,int port){return port>=0&&port<=7&&fold_panel_active_port(root)==port;}
#endif
