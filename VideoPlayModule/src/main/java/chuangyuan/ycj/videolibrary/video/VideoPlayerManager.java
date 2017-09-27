package chuangyuan.ycj.videolibrary.video;


/**
 *
 * Created by yangc on 2017/2/27.
 * E-Mail:1007181167@qq.com
 *  Description： video播放列表控制类
 */
public class VideoPlayerManager {
    private ManualPlayer mVideoPlayer;

    private VideoPlayerManager() {
    }

    public static VideoPlayerManager getInstance() {
        return Holder.holder;
    }

    private static final class Holder {
        static VideoPlayerManager holder = new VideoPlayerManager();
    }

    /***
     * 设置当前播放 控制类
     *
     * @param videoPlayer 播放页
     **/
    public void setCurrentVideoPlayer(ManualPlayer videoPlayer) {
        if (mVideoPlayer != null) {
            releaseVideoPlayer();
        }
        this.mVideoPlayer = videoPlayer;
    }

    /***
     * 释放当前播放
     **/
    public void releaseVideoPlayer() {
        if (mVideoPlayer != null) {
            mVideoPlayer.reset();
            mVideoPlayer = null;
        }
    }

    /***
     * 设置返回建监听
     *
     * @return boolean
     **/
    public boolean onBackPressed() {
        if (mVideoPlayer != null) {
            return mVideoPlayer.onBackPressed();
        }
        return true;
    }

    /**
     * 页面暂停播放暂停
     **/
    public void onPause() {
        if (mVideoPlayer != null) {
            mVideoPlayer.onPause();
        }
    }

    /**
     * 页面恢复
     **/
    public void onResume() {
        if (mVideoPlayer != null) {
            mVideoPlayer.onResume();
        }
    }

    /**
     * 页面销毁
     **/
    public void onDestroy() {
        if (mVideoPlayer != null) {
            mVideoPlayer.onDestroy();
            mVideoPlayer = null;
        }
    }

    /**
     * 获取当前播放类
     *
     * @return ManualPlayer
     **/
    public ManualPlayer getVideoPlayer() {
        return mVideoPlayer;
    }
}
