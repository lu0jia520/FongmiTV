package com.fongmi.android.tv.player;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Utils;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.orhanobut.logger.Logger;

import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.ui.widget.DanmakuView;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class Players implements Player.Listener, IMediaPlayer.Listener, AnalyticsListener, ParseCallback, DrawHandler.Callback {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SYS = 0;
    public static final int IJK = 1;
    public static final int EXO = 2;

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private MediaSessionCompat session;
    private IjkVideoView ijkPlayer;
    private DanmakuView danmuView;
    private StringBuilder builder;
    private Formatter formatter;
    private ExoPlayer exoPlayer;
    private ParseJob parseJob;
    private Runnable runnable;
    private int errorCode;
    private int timeout;
    private int retry;
    private int decode;
    private int player;

    public static boolean isExo(int type) {
        return type == EXO;
    }

    public static boolean isHard() {
        return Setting.getDecode() == HARD;
    }

    public boolean isExo() {
        return player == EXO;
    }

    public boolean isIjk() {
        return player == SYS || player == IJK;
    }

    public Players init(Activity activity) {
        player = Setting.getPlayer();
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        formatter = new Formatter(builder, Locale.getDefault());
        createSession(activity);
        return this;
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setMediaButtonReceiver(null);
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 99, new Intent(App.get(), activity.getClass()), Utils.getPendingFlag()));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void set(PlayerView exo, IjkVideoView ijk) {
        releaseExo();
        releaseIjk();
        setupExo(exo);
        setupIjk(ijk);
    }

    private void setupExo(PlayerView view) {
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setRenderersFactory(ExoUtil.buildRenderersFactory()).setTrackSelector(ExoUtil.buildTrackSelector()).build();
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.addAnalyticsListener(this);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
    }

    private void setupIjk(IjkVideoView view) {
        ijkPlayer = view.render(Setting.getRender()).decode(decode);
        ijkPlayer.addListener(this);
        ijkPlayer.setPlayer(player);
    }

    public void setDanmuView(DanmakuView view) {
        danmuView = view.setCallback(this);
    }

    public ExoPlayer exo() {
        return exoPlayer;
    }

    public IjkVideoView ijk() {
        return ijkPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        session.setMetadata(metadata);
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        if (this.player != player) stop();
        this.player = player;
    }

    public int getDecode() {
        return decode;
    }

    public void setDecode(int decode) {
        this.decode = decode;
    }

    public void reset() {
        removeTimeoutCheck();
        this.errorCode = 0;
        this.retry = 0;
        stopParse();
    }

    public int addRetry() {
        ++retry;
        return retry;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public float getSpeed() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackParameters().speed;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getSpeed();
        return 1.0f;
    }

    public long getPosition() {
        if (isExo() && exoPlayer != null) return exoPlayer.getCurrentPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getCurrentPosition();
        return 0;
    }

    public long getDuration() {
        if (isExo() && exoPlayer != null) return exoPlayer.getDuration();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getDuration();
        return -1;
    }

    public long getBuffered() {
        if (isExo() && exoPlayer != null) return exoPlayer.getBufferedPosition();
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getBufferedPosition();
        return 0;
    }

    public boolean isPlaying() {
        return isExo() ? exoPlayer != null && exoPlayer.isPlaying() : ijkPlayer != null && ijkPlayer.isPlaying();
    }

    public boolean isEnd() {
        if (isExo() && exoPlayer != null) return exoPlayer.getPlaybackState() == Player.STATE_ENDED;
        if (isIjk() && ijkPlayer != null) return ijkPlayer.getPlaybackState() == IjkVideoView.STATE_ENDED;
        return false;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer != null) exoPlayer.setPlaybackSpeed(speed);
        if (ijkPlayer != null) ijkPlayer.setSpeed(speed);
        if (hasDanmu()) danmuView.setSpeed(speed);
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed == 5 ? 0.25f : speed + addon;
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void togglePlayer() {
        setPlayer(isExo() ? SYS : ++player);
    }

    public void nextPlayer() {
        setPlayer(isExo() ? IJK : EXO);
    }

    public void toggleDecode() {
        setDecode(decode == HARD ? SOFT : HARD);
        Setting.putDecode(decode);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time, true);
    }

    public void seekTo(long time, boolean force) {
        if (time == 0 && !force) return;
        if (hasDanmu()) danmuView.seekTo(time);
        if (isExo() && exoPlayer != null) exoPlayer.seekTo(time);
        if (isIjk() && ijkPlayer != null) ijkPlayer.seekTo(time);
    }

    public void play() {
        if (isEnd()) return;
        session.setActive(true);
        if (isExo()) exoPlayer.play();
        if (isIjk()) ijkPlayer.start();
        if (hasDanmu()) danmuView.resume();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (isExo()) pauseExo();
        if (isIjk()) pauseIjk();
        if (hasDanmu()) danmuView.pause();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        reset();
        if (isExo()) stopExo();
        if (isIjk()) stopIjk();
        session.setActive(false);
        if (hasDanmu()) danmuView.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
    }

    public void release() {
        stopParse();
        session.release();
        if (isExo()) releaseExo();
        if (isIjk()) releaseIjk();
        if (hasDanmu()) danmuView.release();
    }

    public boolean isRelease() {
        return exoPlayer == null || ijkPlayer == null;
    }

    public boolean isVod() {
        return getDuration() > 5 * 60 * 1000;
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    public boolean haveTrack(int type) {
        if (isExo()) {
            return ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
        } else {
            return ijkPlayer.haveTrack(type);
        }
    }

    public void start(Channel channel, int timeout) {
        if (isIllegal(channel.getUrl())) {
            ErrorEvent.url();
        } else {
            this.timeout = timeout;
            setMediaSource(channel);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            this.timeout = timeout;
            startParse(result, useParse);
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url();
        } else {
            this.timeout = timeout;
            setMediaSource(result);
        }
    }

    private int getVideoWidth() {
        return isExo() ? exoPlayer.getVideoSize().width : ijkPlayer.getVideoWidth();
    }

    private int getVideoHeight() {
        return isExo() ? exoPlayer.getVideoSize().height : ijkPlayer.getVideoHeight();
    }

    private void pauseExo() {
        exoPlayer.pause();
    }

    private void pauseIjk() {
        ijkPlayer.pause();
    }

    private void stopExo() {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }

    private void stopIjk() {
        ijkPlayer.stop();
    }

    private void releaseExo() {
        if (exoPlayer == null) return;
        exoPlayer.removeListener(this);
        exoPlayer.release();
        exoPlayer = null;
    }

    private void releaseIjk() {
        if (ijkPlayer == null) return;
        ijkPlayer.release();
        ijkPlayer = null;
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
    }

    private void setMediaSource(Result result) {
        Logger.t(TAG).d(errorCode + "," + result.getRealUrl());
        if (isIjk() && ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(result));
        if (isExo() && exoPlayer != null) exoPlayer.setMediaSource(ExoUtil.getSource(result, errorCode));
        if (isExo() && exoPlayer != null) exoPlayer.prepare();
        setTimeoutCheck(result.getRealUrl());
    }

    private void setMediaSource(Channel channel) {
        Logger.t(TAG).d(errorCode + "," + channel.getUrl());
        if (isIjk() && ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(channel));
        if (isExo() && exoPlayer != null) exoPlayer.setMediaSource(ExoUtil.getSource(channel, errorCode));
        if (isExo() && exoPlayer != null) exoPlayer.prepare();
        setTimeoutCheck(channel.getUrl());
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        Logger.t(TAG).d(errorCode + "," + url);
        if (isIjk() && ijkPlayer != null) ijkPlayer.setMediaSource(IjkUtil.getSource(headers, url));
        if (isExo() && exoPlayer != null) exoPlayer.setMediaSource(ExoUtil.getSource(headers, url, errorCode));
        if (isExo() && exoPlayer != null) exoPlayer.prepare();
        setTimeoutCheck(url);
    }

    private void setTimeoutCheck(String url) {
        App.post(runnable, timeout);
        PlayerEvent.url(url);
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    private void setTrack(Track item) {
        if (item.isExo(player)) setTrackExo(item);
        if (item.isIjk(player)) setTrackIjk(item);
    }

    private void setTrackExo(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setTrackIjk(Track item) {
        if (item.isSelected()) {
            ijkPlayer.selectTrack(item.getType(), item.getTrack());
        } else {
            ijkPlayer.deselectTrack(item.getType(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean hasDanmu() {
        return danmuView != null && danmuView.isPrepared();
    }

    private boolean isIllegal(String url) {
        Uri uri = Uri.parse(Util.fixUrl(url));
        String host = Util.host(uri);
        String scheme = Util.scheme(uri);
        if (scheme.equals("data")) return false;
        return scheme.isEmpty() || scheme.equals("file") ? !Path.exists(url) : host.isEmpty();
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        setMediaSource(headers, url);
        if (TextUtils.isEmpty(from)) return;
        Notify.show(ResUtil.getString(R.string.parse_from, from));
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED, EVENT_TIMELINE_CHANGED, EVENT_PLAYBACK_PARAMETERS_CHANGED, EVENT_POSITION_DISCONTINUITY, EVENT_REPEAT_MODE_CHANGED, EVENT_SHUFFLE_MODE_ENABLED_CHANGED, EVENT_MEDIA_METADATA_CHANGED)) return;
        setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        ErrorEvent.format(ExoUtil.getRetry(errorCode = error.errorCode));
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        switch (state) {
            case Player.STATE_READY:
                PlayerEvent.ready();
                break;
            case Player.STATE_BUFFERING:
            case Player.STATE_ENDED:
            case Player.STATE_IDLE:
                PlayerEvent.state(state);
                break;
        }
    }

    @Override
    public void onInfo(IMediaPlayer mp, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                PlayerEvent.state(Player.STATE_BUFFERING);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
            case IMediaPlayer.MEDIA_INFO_VIDEO_SEEK_RENDERING_START:
            case IMediaPlayer.MEDIA_INFO_AUDIO_SEEK_RENDERING_START:
                PlayerEvent.ready();
                break;
        }
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        setPlaybackState(PlaybackStateCompat.STATE_ERROR);
        ErrorEvent.format(1);
        return true;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        PlayerEvent.ready();
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        PlayerEvent.state(Player.STATE_ENDED);
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (danmuView == null) return;
            if (isPlaying() && danmuView.isPrepared()) danmuView.start(getPosition(), Setting.isDanmu());
        });
    }
}
