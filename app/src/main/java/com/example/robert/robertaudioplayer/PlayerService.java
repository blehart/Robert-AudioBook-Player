package com.example.robert.robertaudioplayer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;

public class PlayerService extends Service {
    private static final String LOG = "PlayerService";
    private final IBinder mBinder = new PlayerBinder();
    private Handler mHandler = new Handler();
    private MediaPlayer mPlayer;

    private Playlist playlist;
    private float speed = 1f;
    private long endTime = 0;
    private boolean trackCompleted = false;
    private boolean timerRunning = false;
    private boolean timerPaused = false;


    class PlayerBinder extends Binder {
        PlayerService getService(){
            return PlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(LOG, "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG, "onDestroy");
        if (mPlayer.isPlaying()) mPlayer.stop();
        playlist.save(mPlayer.getCurrentPosition());
        mPlayer.release();
        super.onDestroy();
    }

    public void playPlaylist(Playlist pl){
        Log.i(LOG, "playPlaylist");
//        Intent resultIntent = new Intent(this, MainActivity.class);
//        resultIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//        PendingIntent playPending = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//        Notification notification = new Notification.Builder(this)
//                .setVisibility(Notification.VISIBILITY_PUBLIC)
//                .setSmallIcon(R.drawable.play)
//                .setContentIntent(playPending)
//                .setContentTitle("Robert Audio Player")
//                .setContentText("Hello World")
//                .setOngoing(true)
//                .build();
//        startForeground(mID, notification);
        playlist = pl;
        mPlayer = MediaPlayer.create(this, Uri.parse(playlist.current()));
        mPlayer.seekTo(playlist.getTrackPosition());
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.i(LOG, "CompletionListener");
                try {
                    boolean wasNull = false;
                    if (playlist.next() == null){
                        playlist.jumpTo(0);
                        wasNull = true;
                    }
                    mp.reset();
                    mp.setDataSource(playlist.current());
                    mp.prepare();
                    updateSpeed();
                    if (!wasNull) mp.start();
                    trackCompleted = true;
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean trackCompleted(){
        if (trackCompleted){
            trackCompleted = false;
            return true;
        }
        return false;
    }

    //MediaPlayer Functions
    public void start(){
        mPlayer.start();
        if (timerPaused) resumeTimer();
    }

    public void pause(){
        mPlayer.pause();
        if (timerRunning) pauseTimer();
    }

    public int getDuration(){
        return mPlayer.getDuration();
    }

    public boolean isPlaying(){
        return mPlayer.isPlaying();
    }

    public void seekTo(int value){
        mPlayer.seekTo(value);
    }

    public int getCurrentPosition(){
        return mPlayer.getCurrentPosition();
    }

    //PlaySpeed Functions
    public float getSpeed(){
        return speed;
    }

    public void changeSpeed(float speed){
        this.speed = speed;
        updateSpeed();
    }

    private void updateSpeed(){
        mPlayer.setPlaybackParams(mPlayer.getPlaybackParams().setSpeed(speed));
    }

    //Playlist Functions
    public int getTotalTime(){
        return playlist.getTotalTime();
    }

    public int getElapsedTime(){
        return playlist.getElapsedTime(playlist.getTrack()) + mPlayer.getCurrentPosition() / 1000 * 1000;
    }

    public String getPlaylistName(){
        return playlist.getPlaylistName();
    }

    public String getTrackName(){
        return playlist.getTrackName();
    }

    //Timer Functions
    public long getTimer(){
        return endTime - SystemClock.uptimeMillis();
    }

    public void setTimer(long time){
        endTime = time + SystemClock.uptimeMillis();
        timerRunning = true;
        mHandler.postDelayed(checkTimer, 200);
    }

    public void pauseTimer(){
        timerRunning = false;
        timerPaused = true;
        endTime = endTime - SystemClock.uptimeMillis();
    }

    public void resumeTimer(){
        timerRunning = true;
        timerPaused = false;
        endTime = endTime + SystemClock.uptimeMillis();
    }

    public void stopTimer(){
        mHandler.removeCallbacks(checkTimer);
        timerRunning = false;
    }

    public boolean isTimerRunning(){
        return timerRunning;
    }

    private Runnable checkTimer = new Runnable() {
        @Override
        public void run() {
            if (timerRunning && endTime - SystemClock.uptimeMillis() <= 0){
                if (mPlayer.isPlaying()) mPlayer.pause();
                endTime = 0;
                timerRunning = false;
                playlist.save(mPlayer.getCurrentPosition());
            }
            mHandler.postDelayed(checkTimer, 200);
        }
    };
}
