import yt_dlp
#import ffmpeg
import re
import random
from com.chaquo.python import Python
from com.mvxgreen.ytdloader import MainActivity

def dl_video_without_audio(activity, video_url, out, filename, resolution):
    progress_hook = create_progress_hook(activity)

    # 'format': "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
    # 'outtmpl': out + '%(title).25s.%(ext)s',
    ydl_opts = {
        'format': "bestvideo[height<=" + resolution + "][ext=mp4]/bestvideo[height<=" + resolution + "]/best[height<=" + resolution + "]",
        'outtmpl': out + filename + '.%(ext)s',
        'restrictfilenames': True,
        "cachedir": False,
        "ignoreerrors": True,
        'progress_hooks': [progress_hook]
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=True)
        return info_dict['format_id']


def create_progress_hook(a):
    def progress_hook(d):
        if d['status'] == 'downloading':
            MainActivity.setProgress(d['_percent_str'])
        if d['status'] == 'finished':
            MainActivity.setProgress(d['_percent_str'])
    return progress_hook


def extract_video_title(video_url, resolution):
    # prevent overwrite with random id
    filename_id = f"{random.randint(0,9)}{random.randint(0,9)}{random.randint(0,9)}{random.randint(0,9)}_"

    ydl_opts = {
        'format': "bestvideo[height<=" + resolution + "][ext=mp4]",
        'restrictfilenames': True,
        "cachedir": False,
        "ignoreerrors": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        filename = filename_id + sanitize_filename(info_dict['title'][0:23])
        return filename

def extract_video_ext(video_url, resolution):

    ydl_opts = {
        'format': "bestvideo[height<=" + resolution + "]",
        'restrictfilenames': True,
        "cachedir": False,
        "ignoreerrors": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['ext']

def extract_audio_ext(video_url):

    ydl_opts = {
        'format': "bestaudio",
        'restrictfilenames': True,
        "cachedir": False,
        "ignoreerrors": True,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['ext']

def extract_video_thumbnail(video_url, resolution):
    ydl_opts = {
        'format': "best[height<=" + resolution + "]/bestvideo[height<=" + resolution + "]",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['thumbnail']

def sanitize_filename(filename):
    """Removes or replaces sensitive characters from a filename.

    Args:
        filename (str): The filename to sanitize.

    Returns:
        str: The sanitized filename.
    """

    # 1. Remove or replace characters that are invalid across platforms
    filename = re.sub(r'[<>:"/\\|?*\x00-\x1F]', '_', filename)

    # 2. Remove or replace characters that might cause issues with specific OS
    filename = filename.replace(' ', '_') # replace spaces
    filename = filename.strip('. ')  # Remove leading/trailing spaces and dots

    # 3. Remove potentially problematic characters
    filename = re.sub(r'[,;!@#\$%^&()+]', '', filename)

    # 4. Normalize Unicode characters
    filename = filename.encode('ascii', 'ignore').decode('ascii')

    return filename