/* Diagnostic shell filesystem tool. Fixed disposable target only; no VM APIs. */
#define _FILE_OFFSET_BITS 64
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>
static const char *target="/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img";
static int read_all(int fd,void *p,size_t n){while(n){ssize_t r=read(fd,p,n);if(r<=0)return 0;p=(char*)p+r;n-=r;}return 1;}
int main(int argc,char **argv){
    if(argc!=2)return 64;
    int patch=open(argv[1],O_RDONLY|O_NOFOLLOW),disk=-1,result=1;
    char magic[8];uint64_t size=0;uint32_t count=0;
    if(patch<0||!read_all(patch,magic,8)||memcmp(magic,"UWDP0001",8)||!read_all(patch,&size,8)||!read_all(patch,&count,4)||size!=9126805504ULL||!count||count>=32)goto done;
    disk=open(target,O_RDWR|O_NOFOLLOW);struct stat st;
    if(disk<0||fstat(disk,&st)||!S_ISREG(st.st_mode)||(uint64_t)st.st_size!=size)goto done;
    unsigned char *old=malloc(1048576),*new=malloc(1048576);
    if(!old||!new){free(old);free(new);goto done;}
    uint64_t previous_end=0;
    for(uint32_t i=0;i<count;i++){
        uint64_t offset;uint32_t n,old_crc,new_crc;
        if(!read_all(patch,&offset,8)||!read_all(patch,&n,4)||!read_all(patch,&old_crc,4)||!read_all(patch,&new_crc,4)||!n||n>1048576||offset<previous_end||offset>size||n>size-offset||offset%1048576)goto buffers;
        if(!read_all(patch,new,n)||pread(disk,old,n,(off_t)offset)!=n||crc32(0,old,n)!=old_crc||crc32(0,new,n)!=new_crc)goto buffers;
        if(pwrite(disk,new,n,(off_t)offset)!=n)goto buffers;
        previous_end=offset+n;
    }
    char extra;if(read(patch,&extra,1)!=0||fsync(disk))goto buffers;
    result=0;puts("DISPOSABLE_WINDOWS_DELTA_APPLIED; full SHA must be checked before VM creation");
buffers:free(old);free(new);
done:if(disk>=0)close(disk);if(patch>=0)close(patch);return result;
}
