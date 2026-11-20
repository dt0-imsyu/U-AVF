"""Add a read-only Android descriptor entry point to wimlib 1.14.5."""
import argparse
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path)
source = parser.parse_args().source
path = source / 'src/wim.c'
text = path.read_bytes().decode('utf-8')
if 'uavf_wimlib_open_wim_fd' in text:
    raise SystemExit('Apply to a fresh wimlib 1.14.5 source tree')

def replace(original, replacement):
    global text
    if text.count(original) != 1:
        raise SystemExit('Unexpected wimlib source')
    text = text.replace(original, replacement)

replace('static int\nbegin_read(WIMStruct *wim', '''#ifdef __ANDROID__
#define UAVF_WIMLIB_OPEN_SEEKABLE_FD 0x40000000
#endif

static int
begin_read(WIMStruct *wim''')
replace('''		wim->in_fd.is_pipe = 1;
	} else {
		struct stat stbuf;''', '''		wim->in_fd.is_pipe = 1;
#ifdef __ANDROID__
	} else if (open_flags & UAVF_WIMLIB_OPEN_SEEKABLE_FD) {
		struct stat stbuf;
		int raw_fd = fcntl(*(const int *)wim_filename_or_fd, F_DUPFD_CLOEXEC, 0);
		if (raw_fd < 0)
			return WIMLIB_ERR_OPEN;
		filedes_init(&wim->in_fd, raw_fd);
		if (fstat(raw_fd, &stbuf) != 0 || !S_ISREG(stbuf.st_mode) || stbuf.st_size <= 0)
			return WIMLIB_ERR_OPEN;
		wim->file_size = stbuf.st_size;
		wimfile = T("<read-only ISO payload>");
#endif
	} else {
		struct stat stbuf;''')
replace('/* API function documented in wimlib.h  */\nWIMLIBAPI int\nwimlib_open_wim_with_progress', '''#ifdef __ANDROID__
int
uavf_wimlib_open_wim_fd(int fd, WIMStruct **wim_ret)
{
	if (fd < 0 || !wim_ret)
		return WIMLIB_ERR_INVALID_PARAM;
	return open_wim_as_WIMStruct(&fd, UAVF_WIMLIB_OPEN_SEEKABLE_FD |
	                            WIMLIB_OPEN_FLAG_ERROR_IF_SPLIT, wim_ret, NULL, NULL);
}
#endif

/* API function documented in wimlib.h  */
WIMLIBAPI int
wimlib_open_wim_with_progress''')
path.write_bytes(text.encode('utf-8'))

# XML reads need a live input descriptor. Overwrite and mount checks still
# require a real filename, which is deliberately absent for this input.
path = source / 'src/xml.c'
text = path.read_bytes().decode('utf-8')
replace('if (wim->filename == NULL && filedes_is_seekable(&wim->in_fd))',
        'if (wim->filename == NULL && !filedes_valid(&wim->in_fd))')
path.write_bytes(text.encode('utf-8'))
