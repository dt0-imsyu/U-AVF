/* Read-only bounded Android shell receiver for isolated Windows UVF1 proof.
 * Not an Android presentation test. No VM/device/storage mutation. */
#include <sys/socket.h>
#include <linux/vm_sockets.h>
#include <unistd.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
static int read_all(int fd,void *buf,size_t n){char *p=buf;while(n){ssize_t r=read(fd,p,n);if(r<=0)return 0;p+=r;n-=r;}return 1;}
#pragma pack(push,1)
typedef struct {char magic[4];uint16_t version,kind;uint32_t codec,generation,sequence;
    uint16_t width,height;uint64_t pts;uint32_t length,reserved;} Header;
#pragma pack(pop)
int main(int argc,char **argv){
    if(argc!=2)return 2;unsigned cid=(unsigned)strtoul(argv[1],NULL,10);if(cid<3)return 2;
    int fd=-1;
    for(int i=0;i<60;i++){
        fd=socket(AF_VSOCK,SOCK_STREAM,0);if(fd<0){perror("socket");return 3;}
        struct timeval t={3,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&t,sizeof(t));setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&t,sizeof(t));
        struct sockaddr_vm addr={.svm_family=AF_VSOCK,.svm_port=4055,.svm_cid=cid};
        if(!connect(fd,(struct sockaddr*)&addr,sizeof(addr)))break;
        int error=errno;close(fd);fd=-1;
        if(error==EACCES||error==EPERM){fprintf(stderr,"connect permission denied\n");return 4;}sleep(1);
    }
    if(fd<0)return 5;
    unsigned char *payload=malloc(4194304);if(!payload){close(fd);return 6;}
    unsigned frames=0;uint64_t bytes=0;uint32_t last=0,gen=0;int result=1;
    for(int i=0;i<64;i++){
        Header h;if(!read_all(fd,&h,40))break;
        if(memcmp(h.magic,"UVF1",4)||h.version!=1||h.codec!=1||h.width!=640||h.height!=480||h.length>4194304)break;
        if(h.kind==4&&!h.length){struct timespec ts;clock_gettime(CLOCK_MONOTONIC,&ts);uint64_t ns=(uint64_t)ts.tv_sec*1000000000+ts.tv_nsec;if(write(fd,&ns,8)!=8)break;gen=h.generation;continue;}
        if(h.generation!=gen||h.sequence<=last)break;last=h.sequence;
        if(h.kind==3&&!h.length){result=frames==30?0:1;break;}
        if(h.kind!=2||!h.length||!read_all(fd,payload,h.length))break;
        if(fwrite(&h,1,40,stdout)!=40||fwrite(payload,1,h.length,stdout)!=h.length)break;
        frames++;bytes+=h.length;
    }
    fflush(stdout);fprintf(stderr,"UVF_REPLAY result=%d frames=%u bytes=%llu\n",result,frames,(unsigned long long)bytes);
    free(payload);close(fd);return result;
}
