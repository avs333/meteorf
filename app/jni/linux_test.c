#include <stdio.h>

#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>


#include "bzlib.h"


#define INFILE "stlist.bz2"
#define OUTFILE "st_out" 

#define OUTBUFSZ (16*1024)

void dump(bz_stream *strm, char *pref) 
{
	printf("%s IN: next = %p, avail = %d, total = %d\n", pref, strm->next_in, strm->avail_in, strm->total_in_lo32);
	printf("%s OUT: next = %p, avail = %d, total = %d\n", pref, strm->next_out, strm->avail_out, strm->total_out_lo32);
}

void bz_internal_error ( int errcode ) 
{
    printf("internal error %d\n", errcode);		
}

int main()
{
    void *mem;
    int fd, k, fdout, to_write;
    struct stat st;
    bz_stream *strm;
    char *out;

	fd = open(INFILE, O_RDONLY);
	if(fd < 0) return printf(INFILE ": open failed\n");

	fdout = open(OUTFILE, O_RDWR|O_CREAT|O_TRUNC, 0666);
	if(fdout < 0) return printf(OUTFILE ": open failed\n");
	
	k = fstat(fd, &st);
	if(k != 0) return printf(INFILE ": fstat failed\n");

	mem = mmap(0, st.st_size, PROT_READ, MAP_SHARED, fd, 0);
	if(mem == MAP_FAILED) return printf(INFILE ": map failed\n"); 	

	strm = (bz_stream *) calloc(1, sizeof(bz_stream));
	if(!strm) return printf("calloc failed for bz stream\n");

	out = (char *) malloc(OUTBUFSZ);
	if(!out) return printf("malloc failed for output buffer\n");
	
	k = BZ2_bzDecompressInit(strm, 3, 0);
	
	if(k != BZ_OK) return printf("BZ2_bzDecompressInit error %d\n", k);	

	strm->avail_in = st.st_size;
	strm->next_in = (char *) mem; 


	do {
	    strm->next_out = (char *) out;
	    strm->avail_out = OUTBUFSZ;
	    dump(strm, "pre");	
	    k = BZ2_bzDecompress(strm); 	
	    dump(strm, "post");	

	    if(k != BZ_STREAM_END && k != BZ_OK) return printf("bzDecompress error %d\n", k);
	    to_write = OUTBUFSZ - strm->avail_out;	
	    if(to_write != 0) {
		int i = write(fdout, out, to_write);
		if(i != to_write) return printf("write error %d %d\n", i, to_write);
		printf("wrote %d\n", i);
	    }	
	    if(k == BZ_STREAM_END) break;
	} while(1); 

	close(fdout);
	k = BZ2_bzDecompressEnd(strm);
	

	munmap(mem, st.st_size);


}




