import yt_dlp
from com.chaquo.python import Python
from com.mvxgreen.ytdloader import MainActivity

def download_video(activity, video_url, out):
    # 'outtmpl': out + '%(title)s.%(ext)s',
    progress_hook = create_progress_hook(activity)
    ydl_opts = {
        #'format': "bestvideo",
        'outtmpl': out + '%(title).25s.%(ext)s',
        'progress_hooks': [progress_hook]
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=True)
        return info_dict['title'][0:25]

def create_progress_hook(a):
    def progress_hook(d):
        if d['status'] == 'downloading':
            MainActivity.setProgress(a, d['_percent_str'])
        if d['status'] == 'finished':
            MainActivity.setProgress(a, d['_percent_str'])
    return progress_hook


def extract_video_title(video_url):
    ydl_opts = {
        #'format': "bestvideo",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['title']

def extract_video_ext(video_url):
    ydl_opts = {
        #'format': "bestvideo",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['ext']

def extract_video_dl_url(video_url):
    ydl_opts = {
        #'format': "bestvideo",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['url']

def extract_video_thumbnail(video_url):
    ydl_opts = {
        #'format': "bestvideo",
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info_dict = ydl.extract_info(video_url, download=False)
        return info_dict['thumbnail']