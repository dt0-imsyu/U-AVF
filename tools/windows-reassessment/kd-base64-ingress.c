/* Unbuffered Base64 ingress. Decode each quartet immediately, including
 * padded independent records; never wait for pipe EOF or a 4 KiB buffer. */
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <stdint.h>
#include <time.h>
static void paced_commit(int *remaining,int used,uint64_t *waits) {
  *remaining-=used;
  if(!*remaining){usleep(1000);*remaining=8;(*waits)++;}
}
static int append_bytes(int fd,const unsigned char *bytes,int length) {
  int off=0;
  while(off<length){ssize_t n=write(fd,bytes+off,length-off);if(n<0&&errno==EINTR)continue;if(n<=0)return -1;off+=(int)n;}
  return 0;
}
static int digit(unsigned char c) {
  if(c>='A'&&c<='Z')return c-'A';
  if(c>='a'&&c<='z')return c-'a'+26;
  if(c>='0'&&c<='9')return c-'0'+52;
  if(c=='+')return 62;if(c=='/')return 63;if(c=='=')return 64;
  return -1;
}
int main(int argc, char **argv) {
  if(argc==2&&!strcmp(argv[1],"--pacing-selftest")){
    int a=8,b=8;uint64_t aw=0,bw=0;
    for(int i=0;i<8;i++)paced_commit(&a,8,&aw);
    for(int i=0;i<64;i++)paced_commit(&b,1,&bw);
    if(a!=8||b!=8||aw!=8||bw!=8)return 11;
    puts("UART_PACING_FRAGMENT_INDEPENDENT_ACTUAL_CODE=PASS");return 0;
  }
  int paced=0,logfd=-1,writtenfd=-1,pace_remaining=8;uint64_t accepted=0,pace_waits=0;unsigned writes=0;
  for(int i=1;i<argc;i++) {
    if(!strcmp(argv[i],"--paced"))paced=1;
    else if(!strcmp(argv[i],"--log")&&i+1<argc) {
      logfd=open(argv[++i],O_CREAT|O_TRUNC|O_WRONLY,0600);
      if(logfd<0)return 6;
    } else if(!strcmp(argv[i],"--written-log")&&i+1<argc) {
      writtenfd=open(argv[++i],O_CREAT|O_TRUNC|O_WRONLY,0600);
      if(writtenfd<0)return 9;
    } else return 7;
  }
  unsigned char in[4096],out[3072];int q[4],count=0;
  for(;;){
    ssize_t n=read(0,in,sizeof(in));
    if(n<0&&errno==EINTR)continue;if(n<0)return 1;if(!n)return count?2:0;
    int used=0;
    for(ssize_t i=0;i<n;i++){
      unsigned char c=in[i];if(c=='\n'||c=='\r'||c==' '||c=='\t')continue;
      int d=digit(c);if(d<0)return 3;q[count++]=d;if(count!=4)continue;count=0;
      if(q[0]==64||q[1]==64||(q[2]==64&&q[3]!=64))return 4;
      int len=q[2]==64?1:q[3]==64?2:3;
      out[used]=(q[0]<<2)|(q[1]>>4);out[used+1]=(q[1]<<4)|(q[2]>>2);out[used+2]=(q[2]<<6)|q[3];
      used+=len;
    }
    // Flush immediately after this read, not after each 3-byte quartet.
    // No EOF/newline/large-buffer wait; preserves short open-stream latency.
    if(logfd>=0) {
      int off=0;
      while(off<used){ssize_t w=write(logfd,out+off,used-off);if(w<0&&errno==EINTR)continue;if(w<=0)return 8;off+=w;}
    }
    int off=0;
    while(off<used){
      int len=used-off;if(paced&&len>pace_remaining)len=pace_remaining;
      ssize_t w=write(1,out+off,len);
      if(w<0&&errno==EINTR)continue;
      if(w<=0){if(writtenfd>=0)fprintf(stderr,"UART_PIPE_WRITE_FAILURE errno=%d accepted=%llu requested=%d\n",errno,(unsigned long long)accepted,len);return 5;}
      if(writtenfd>=0&&append_bytes(writtenfd,out+off,(int)w)<0)return 10;
      off+=(int)w;accepted+=(uint64_t)w;writes++;
      // TCP/Base64 read boundaries are arbitrary. Preserve the proven byte
      // rate across reads, not one millisecond per 1-byte fragment. Large
      // 8-byte writes keep their previous pacing; partial writes share credit.
      if(paced)paced_commit(&pace_remaining,(int)w,&pace_waits);
      if(writtenfd>=0&&(writes<=32||writes%128==0||off==used)){
        int queued=-1;int rc=ioctl(1,FIONREAD,&queued);int error=rc<0?errno:0;
        struct timespec now;clock_gettime(CLOCK_MONOTONIC,&now);
        fprintf(stderr,"UART_PIPE_ACCEPT accepted=%llu chunk=%ld queued=%d ioctl_errno=%d pace_waits=%llu monotonic_ns=%llu\n",(unsigned long long)accepted,(long)w,queued,error,(unsigned long long)pace_waits,(unsigned long long)now.tv_sec*1000000000ull+(unsigned long long)now.tv_nsec);
      }
    }
  }
}
