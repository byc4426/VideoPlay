
package chuangyuan.ycj.videolibrary.video;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import chuangyuan.ycj.videolibrary.listener.DataSourceListener;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerListener;
import chuangyuan.ycj.videolibrary.listener.ExoPlayerViewListener;
import chuangyuan.ycj.videolibrary.listener.VideoInfoListener;
import chuangyuan.ycj.videolibrary.utils.VideoPlayUtils;
import chuangyuan.ycj.videolibrary.widget.VideoPlayerView;

public class ExoUserPlayer {

    private static final String TAG = ExoUserPlayer.class.getName();
    private long lastTotalRxBytes = 0;//获取网速大小
    private long lastTimeStamp = 0;
    private long resumePosition;//进度
    private int resumeWindow;
    private Timer timer;//定时任务类
    SimpleExoPlayer player;
    VideoPlayerView mPlayerView;
    ExoPlayerViewListener mPlayerViewListener;
    private VideoInfoListener videoInfoListener;//回调信息
    private boolean playerNeedsSource;
    private NetworkBroadcastReceiver mNetworkBroadcastReceiver;
    List<String> videoUri;
    List<String> nameUri;
    Activity activity;
    private ComponentListener componentListener;
    private PlayComponentListener playComponentListener;
    private boolean isPause;
    MediaSourceBuilder mediaSourceBuilder;

    /****
     * 初始化
     *
     * @param activity   活动对象
     * @param playerView 播放控件
     **/
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView) {
        this(activity, playerView, null);
    }

    /****
     * @param activity 活动对象
     * @param reId     播放控件id
     **/
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId) {
        this(activity, (VideoPlayerView) activity.findViewById(reId));
    }

    /****
     * 初始化
     *
     * @param activity 活动对象
     * @param reId     播放控件id
     * @param listener 自定义数据源类
     **/
    public ExoUserPlayer(@NonNull Activity activity, @IdRes int reId, @Nullable DataSourceListener listener) {
        this(activity, (VideoPlayerView) activity.findViewById(reId), listener);
    }

    /****
     * 初始化
     *
     * @param activity   活动对象
     * @param playerView 播放控件
     * @param listener   自定义数据源类
     **/
    public ExoUserPlayer(@NonNull Activity activity, @NonNull VideoPlayerView playerView, @Nullable DataSourceListener listener) {
        this.activity = activity;
        this.mPlayerView = playerView;
        mediaSourceBuilder = new MediaSourceBuilder(listener);
        initView();
    }

    private void initView() {
        playComponentListener = new PlayComponentListener();
        componentListener = new ComponentListener();
        mPlayerView.setExoPlayerListener(playComponentListener);
        mPlayerViewListener = mPlayerView.getComponentListener();
        timer = new Timer();
        timer.schedule(task, 0, 1000); // 1s后启动任务，每1s执行一次
    }

    /**
     * 设置播放路径
     *
     * @param uri 路径
     ***/
    public void setPlayUri(@NonNull String uri) {
        setPlayUri(Uri.parse(uri));
    }

    /***
     * 设置进度
     *
     * @param resumePosition 毫秒
     **/
    public void setPosition(long resumePosition) {
        this.resumePosition = resumePosition;
    }

    /***
     * 是否隐藏
     **/
    void hslHideView() {
        if (mediaSourceBuilder.getStreamType() == C.TYPE_HLS) {//直播隐藏进度条
            mPlayerViewListener.showHidePro(View.INVISIBLE);
        } else {
            mPlayerViewListener.showHidePro(View.VISIBLE);
        }
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     **/
    public void setPlaySwitchUri(@NonNull String[] videoUri, @NonNull String[] name) {
        setPlaySwitchUri(Arrays.asList(videoUri), Arrays.asList(name));
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     * @param index    选中播放线路
     **/
    public void setPlaySwitchUri(@NonNull String[] videoUri, @NonNull String[] name, int index) {
        setPlaySwitchUri(Arrays.asList(videoUri), Arrays.asList(name), index);
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     **/
    public void setPlaySwitchUri(@NonNull List<String> videoUri, @NonNull List<String> name) {
        setPlaySwitchUri(videoUri, name, 0);
    }

    /**
     * 设置多线路播放
     *
     * @param videoUri 视频地址
     * @param name     清清晰度显示名称
     * @param index    选中播放线路
     **/
    public void setPlaySwitchUri(@NonNull List<String> videoUri, @NonNull List<String> name, int index) {
        this.videoUri = videoUri;
        this.nameUri = name;
        mPlayerViewListener.showSwitchName(nameUri.get(index));
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), Uri.parse(videoUri.get(index)));
        createPlayers();
        hslHideView();
        registerReceiverNet();
    }

    /****
     * @param firstVideoUri  预览的视频
     * @param secondVideoUri 第二个视频
     **/
    public void setPlayUri(@NonNull String firstVideoUri, @NonNull String secondVideoUri) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), firstVideoUri, secondVideoUri);
        createPlayers();
        hslHideView();
        registerReceiverNet();
    }

    /**
     * 设置播放路径
     *
     * @param uri 路径
     ***/
    public void setPlayUri(@NonNull Uri uri) {
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), uri);
        createPlayers();
        hslHideView();
        registerReceiverNet();
    }

    /***
     * 页面恢复处理
     **/
    public void onResume() {
        if ((Util.SDK_INT <= 23 || player == null)) {
            createPlayers();
            player.getBufferedPosition();
        }
    }

    /***
     * 页面暂停处理
     **/
    public void onPause() {
        if (player != null) {
            isPause = !player.getPlayWhenReady();
            releasePlayers();
        }
    }

    /**
     * 页面销毁处理
     **/
    public void onDestroy() {
        releasePlayers();
    }


    /***
     * 释放资源
     **/
    public void releasePlayers() {
        if (player != null) {
            updateResumePosition();
            unNetworkBroadcastReceiver();
            player.stop();
            player.release();
            player.removeListener(componentListener);
            player.clearVideoSurface();
            player = null;
        }
        if (mediaSourceBuilder != null) {
            mediaSourceBuilder.release();
        }
        if (activity.isFinishing()) {
            if (timer != null) {
                timer.cancel();
            }
            if (task != null) {
                task.cancel();
            }
            mPlayerView.setExoPlayerListener(null);
            playComponentListener = null;
        }
    }

    /****
     * 创建
     **/
    protected void createPlayers() {
        if (player == null) {
            player = createFullPlayer();
            playerNeedsSource = true;
        }
        playVideo();

    }

    /****
     * 创建
     **/
    protected void createPlayersNo() {
        if (player == null) {
            player = createFullPlayer();
            playerNeedsSource = true;
        }
    }

    /****
     * 创建
     **/
    protected void createPlayersPlay() {
        player = createFullPlayer();
    }

    private SimpleExoPlayer createFullPlayer() {
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(activity, trackSelector);
        mPlayerView.setPlayer(player);
        return player;
    }

    /***
     * 播放视频
     **/
    public void playVideo() {
        if (VideoPlayUtils.isWifi(activity)) {
            onPlayNoAlertVideo();
        } else {
            mPlayerViewListener.showAlertDialog();
        }
    }

    /***
     * 播放视频
     **/

    protected void onPlayNoAlertVideo() {
        if (player == null) {
            createPlayersPlay();
        }
        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
        if (haveResumePosition) {
            player.seekTo(resumeWindow, resumePosition);
        }
        if (isPause) {
            player.setPlayWhenReady(false);
        } else {
            player.setPlayWhenReady(true);
        }
        player.prepare(mediaSourceBuilder.getMediaSource(), !haveResumePosition, true);
        player.addListener(componentListener);
        playerNeedsSource = false;
    }

    /****
     * 重置进度
     **/
    protected void updateResumePosition() {
        if (player != null) {
            resumeWindow = player.getCurrentWindowIndex();
            resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
                    : C.TIME_UNSET;
        }
    }

    /**
     * 清除进度
     ***/
    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    /***
     * 网络变化任务
     **/
    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            if (mPlayerView.isLoadingLayoutShow()) {
                mPlayerViewListener.showNetSpeed(getNetSpeed());
            }
        }
    };

    /****
     * 获取当前网速
     *
     * @return String 二返回当前网速字符
     **/
    private String getNetSpeed() {
        String netSpeed;
        long nowTotalRxBytes = VideoPlayUtils.getTotalRxBytes(activity);
        long nowTimeStamp = System.currentTimeMillis();
        long calculationTime = (nowTimeStamp - lastTimeStamp);
        if (calculationTime == 0) {
            netSpeed = String.valueOf(1) + " kb/s";
            return netSpeed;
        }
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / calculationTime);//毫秒转换
        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        if (speed > 1024) {
            DecimalFormat df = new DecimalFormat("######0.0");
            netSpeed = String.valueOf(df.format(VideoPlayUtils.getM(speed))) + " MB/s";
        } else {
            netSpeed = String.valueOf(speed) + " kb/s";
        }
        return netSpeed;
    }

    /****
     * 监听返回键 true 可以正常返回处理，false 切换到竖屏
     *
     * @return boolean
     ***/
    public boolean onBackPressed() {
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mPlayerView.exitFullView();
            return false;
        } else {
            return true;
        }
    }

    /****
     * 滑动音量
     *
     * @param state 完成是否
     **/
    void showReplay(int state) {
    }

    public VideoPlayerView getPlayerView() {
        return mPlayerView;
    }

    /****
     * 横竖屏切换
     *
     * @param configuration 旋转
     ***/
    public void onConfigurationChanged(Configuration configuration) {
        mPlayerViewListener.onConfigurationChanged(configuration.orientation);
    }

    /***
     * 显示水印图
     *
     * @param res 资源
     ***/
    public void setExoPlayWatermarkImg(int res) {
        mPlayerViewListener.setWatermarkImage(res);
    }

    public void setTitle(@NonNull String title) {
        mPlayerViewListener.setTitle(title);
    }

    public void setVideoInfoListener(VideoInfoListener videoInfoListener) {
        this.videoInfoListener = videoInfoListener;
    }

    public void setShowVideoSwitch(boolean showVideoSwitch) {
        mPlayerView.setShowVideoSwitch(showVideoSwitch);
    }

    public SimpleExoPlayer getPlayer() {
        return player;
    }

    /**
     * 返回视频总进度  以毫秒为单位
     *
     * @return long
     **/
    public long getDuration() {
        return player == null ? 0 : player.getDuration();
    }

    /**
     * 返回视频当前播放进度  以毫秒为单位
     *
     * @return long
     **/
    public long getCurrentPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    /**
     * 返回视频当前播放d缓冲进度  以毫秒为单位
     *
     * @return long
     **/
    public long getBufferedPosition() {
        return player == null ? 0 : player.getBufferedPosition();
    }

    /***
     * 注册广播监听
     **/
    protected void registerReceiverNet() {
        if (mNetworkBroadcastReceiver == null) {
            IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            mNetworkBroadcastReceiver = new NetworkBroadcastReceiver();
            activity.registerReceiver(mNetworkBroadcastReceiver, intentFilter);
        }
    }

    /***
     * 取消广播监听
     **/
    private void unNetworkBroadcastReceiver() {
        if (mNetworkBroadcastReceiver != null) {
            activity.unregisterReceiver(mNetworkBroadcastReceiver);
        }
        mNetworkBroadcastReceiver = null;
    }

    /***
     * 网络监听类
     ***/
    private class NetworkBroadcastReceiver extends BroadcastReceiver {
        long is = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                ConnectivityManager mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = mConnectivityManager.getActiveNetworkInfo();
                if (netInfo != null && netInfo.isAvailable()) {
                    /////////////网络连接
                    if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                        /////WiFi网络
                    } else if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                        /////////3g网络
                        if (System.currentTimeMillis() - is > 500) {
                            is = System.currentTimeMillis();
                            updateResumePosition();
                            releasePlayers();
                            mPlayerViewListener.showAlertDialog();
                        }
                    }
                    Log.d(TAG, "onReceive:" + netInfo.getType() + "__:");
                }
            }

        }
    }

    private class PlayComponentListener implements ExoPlayerListener {
        @Override
        public void onCreatePlayers() {
            createPlayers();
        }

        @Override
        public void onClearPosition() {
            clearResumePosition();

        }

        @Override
        public void replayPlayers() {
            clearResumePosition();
            onPlayNoAlertVideo();
        }

        @Override
        public void switchUri(int position, String name) {
            if (mediaSourceBuilder != null) {
                mediaSourceBuilder.release();
            }
            mediaSourceBuilder.setMediaSourceUri(activity.getApplicationContext(), Uri.parse(videoUri.get(position)));
            updateResumePosition();
            onPlayNoAlertVideo();
        }

        @Override
        public void playVideoUri() {
            onPlayNoAlertVideo();

        }

        @Override
        public ExoUserPlayer getPlay() {
            return ExoUserPlayer.this;
        }

        @Override
        public void showReplayViewChange(int visibility) {
            showReplay(visibility);
        }

        @Override
        public void onBack() {
            if (activity != null) {
                activity.onBackPressed();
            }
        }
    }

    private class ComponentListener implements ExoPlayer.EventListener {
        /***
         * 视频播放播放
         **/
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
            Log.d(TAG, "onTimelineChanged:Timeline:getPeriodCount" + timeline.getPeriodCount());
            if (timeline.getPeriodCount() > 1) {
                if (player.getCurrentTrackGroups().length == 0) {
                    mPlayerViewListener.showHidePro(View.INVISIBLE);
                } else {
                    mPlayerViewListener.showHidePro(View.VISIBLE);
                }
            }
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            Log.d(TAG, "onTracksChanged:" + trackGroups.length);

        }

        /*****
         * 进度条控制 加载页
         *********/
        @Override
        public void onLoadingChanged(boolean isLoading) {
            Log.d(TAG, "onLoadingChanged:" + isLoading + "" + player.getPlayWhenReady());
        }

        /**
         * 视频的播放状态
         * STATE_IDLE 播放器空闲，既不在准备也不在播放
         * STATE_PREPARING 播放器正在准备
         * STATE_BUFFERING 播放器已经准备完毕，但无法立即播放。此状态的原因有很多，但常见的是播放器需要缓冲更多数据才能开始播放
         * STATE_PAUSE 播放器准备好并可以立即播放当前位置
         * STATE_PLAY 播放器正在播放中
         * STATE_ENDED 播放已完毕
         */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//防锁屏

            } else {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);//防锁屏
            }
            Log.d(TAG, "onPlayerStateChanged:+playWhenReady:" + playWhenReady);
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    Log.d(TAG, "onPlayerStateChanged:加载中。。。");
                    if (playWhenReady) {
                        mPlayerViewListener.showLoadStateView(View.VISIBLE);
                    }
                    if (videoInfoListener != null) {
                        videoInfoListener.onLoadingChanged();
                    }
                    break;
                case Player.STATE_ENDED:
                    Log.d(TAG, "onPlayerStateChanged:ended。。。");
                    mPlayerViewListener.showReplayView(View.VISIBLE);
                    if (videoInfoListener != null) {
                        videoInfoListener.onPlayEnd();
                    }
                    break;
                case Player.STATE_IDLE://空的
                    Log.d(TAG, "onPlayerStateChanged::网络状态差，请检查网络。。。");
                    updateResumePosition();
                    if (!VideoPlayUtils.isNetworkAvailable(activity)) {
                        if (playerNeedsSource) {
                            mPlayerViewListener.showErrorStateView(View.VISIBLE);
                        }
                    } else {
                        mPlayerViewListener.showErrorStateView(View.VISIBLE);
                    }
                    break;
                case Player.STATE_READY:
                    Log.d(TAG, "onPlayerStateChanged:ready。。。");
                    mPlayerViewListener.showLoadStateView(View.GONE);
                    if (videoInfoListener != null) {
                        videoInfoListener.onPlayStart();
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            if (videoInfoListener != null) {
                videoInfoListener.onRepeatModeChanged(repeatMode);
            }

        }

        @Override
        public void onPlayerError(ExoPlaybackException e) {
            Log.e(TAG, "onPlayerError:" + e.getMessage());
            playerNeedsSource = true;
            if (VideoPlayUtils.isBehindLiveWindow(e)) {
                clearResumePosition();
                playVideo();
            } else {
                mPlayerViewListener.showErrorStateView(View.VISIBLE);
                if (videoInfoListener != null) {
                    videoInfoListener.onPlayerError(e);
                }
            }
        }

        @Override
        public void onPositionDiscontinuity() {
            Log.d(TAG, "onPositionDiscontinuity:");
            if (playerNeedsSource) {
                updateResumePosition();
            }
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }
    }
}

