#ifndef HYPERLUX_PANEL_NODE_H
#define HYPERLUX_PANEL_NODE_H
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
/* The Java resolver chooses one device; the writer independently limits its namespace. */
#define PANEL_NODE_PATH_SIZE 256
#define PANEL_REAL_PATH_SIZE 768
#define PANEL_LEGACY_NODE "/sys/class/backlight/panel0-backlight/brightness"
static inline int panel_class_path(const char *path){
 const char *backlight="/sys/class/backlight/",*leds="/sys/class/leds/";int is_led=0;
 const char *name=NULL;if(!strncmp(path,backlight,strlen(backlight)))name=path+strlen(backlight);
 else if(!strncmp(path,leds,strlen(leds))){name=path+strlen(leds);is_led=1;}else return 0;
 const char *end=strchr(name,'/');if(!end||strcmp(end,"/brightness"))return 0;
 size_t len=(size_t)(end-name);if(!len||len>96)return 0;
 char lower[97];for(size_t i=0;i<len;i++){unsigned char ch=(unsigned char)name[i];if(!isalnum(ch)&&ch!='-'&&ch!='_'&&ch!='.'&&ch!=':')return 0;lower[i]=(char)tolower(ch);}lower[len]=0;
 if(!strcmp(lower,".")||!strcmp(lower,".."))return 0;
 const char *deny[]={"secondary","second","aux","cover","rear","subdisplay","sub-display","sub_backlight","sub-backlight","outer","keyboard","kbd","button","torch","flash","notification","indicator","rgb"};
 for(size_t i=0;i<sizeof(deny)/sizeof(deny[0]);i++)if(strstr(lower,deny[i]))return 0;
 const char *screen[]={"panel","display","lcd"};
 for(size_t i=0;i<sizeof(screen)/sizeof(screen[0]);i++){
  const char *p=lower;while((p=strstr(p,screen[i]))){p+=strlen(screen[i]);if(*p=='-'||*p=='_')p++;if(*p>='1'&&*p<='9')return 0;}
 }
 if(!is_led)return 1;
 const char *allowed[]={"panel0-backlight","panel0","display0-backlight","display0","lcd0-backlight","lcd0","lcd-backlight","lcd_backlight","lcd","display-backlight","display_backlight","mtk-lcd-backlight","mtk-lcd","mtk-backlight","mtk-bl","mdss-bl","primary-backlight"};
 for(size_t i=0;i<sizeof(allowed)/sizeof(allowed[0]);i++)if(!strcmp(lower,allowed[i]))return 1;
 return 0;
}
static inline int panel_boot_token(const char *s){
 if(strlen(s)!=36)return 0;for(int i=0;i<36;i++){if(i==8||i==13||i==18||i==23){if(s[i]!='-')return 0;}else if(!isxdigit((unsigned char)s[i]))return 0;}return 1;
}
struct panel_permissions {unsigned mode;unsigned long long device,inode;char boot[40],path[PANEL_NODE_PATH_SIZE],real[PANEL_REAL_PATH_SIZE];int legacy;};
static inline int panel_permissions_parse(const char *text,struct panel_permissions *record){
 memset(record,0,sizeof(*record));char tail;
 if(!strncmp(text,"v2\n",3)){
  if(sscanf(text,"v2\n%o\n%llu %llu\n%39s\n%255s\n%767s %c",&record->mode,&record->device,&record->inode,record->boot,record->path,record->real,&tail)!=6)return 0;
  if(!panel_boot_token(record->boot)||!panel_class_path(record->path)||strncmp(record->real,"/sys/devices/",13))return 0;
 }else{
  if(sscanf(text,"%o %c",&record->mode,&tail)!=1)return 0;
  record->legacy=1;strcpy(record->path,PANEL_LEGACY_NODE);
 }
 return record->mode<=0777;
}
static inline int panel_permissions_format(char *text,size_t size,const struct panel_permissions *record){
 return snprintf(text,size,"v2\n%o\n%llu %llu\n%s\n%s\n%s\n",record->mode,record->device,record->inode,record->boot,record->path,record->real);
}
static inline int panel_permissions_new_boot(const struct panel_permissions *record,const char *boot){return !record->legacy&&strcmp(record->boot,boot)!=0;}
static inline int panel_permissions_matches(const struct panel_permissions *record,const char *real,unsigned long long device,unsigned long long inode){
 if(strncmp(real,"/sys/devices/",13))return 0;
 return record->legacy||(!strcmp(record->real,real)&&record->device==device&&record->inode==inode);
}
#endif
