package com.example.robert.robertaudioplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private String rootDirectory;
    private ImageButton playpauseButton;
    private SeekBar mTrackSeekBar, mListSeekBar, mTimerSeekbar;
    private TextView tvCurrentTrackTime, tvEndTrackTime;
    private TextView tvCurrentListTime, tvEndListTime;
    private TextView tvPlaySpeed;
    private TextView tvTimerTime;
    private TextView tvCurrentPlaylist;
    private TextView tvCurrentFile;

    private Handler mHandler = new Handler();

    private PlayerService mService;
    private boolean mBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "OnServiceConnected");
            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
            mService = binder.getService();
            loadPlaylist();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);

        playpauseButton = (ImageButton) findViewById(R.id.playpauseButton);

        mTrackSeekBar = (SeekBar) findViewById(R.id.trackSeekBar);
        mListSeekBar = (SeekBar)findViewById(R.id.listSeekBar);
        mTimerSeekbar = (SeekBar)findViewById(R.id.timerSeekBar);

        tvCurrentTrackTime = (TextView)findViewById(R.id.currentTrackTime);
        tvCurrentListTime = (TextView)findViewById(R.id.currentListTime);
        tvEndTrackTime = (TextView)findViewById(R.id.endTrackTime);
        tvEndListTime = (TextView) findViewById(R.id.endListTime);
        tvPlaySpeed = (TextView)findViewById(R.id.playSpeed);
        tvTimerTime = (TextView)findViewById(R.id.currentTimerTime);
        tvCurrentPlaylist = (TextView) findViewById(R.id.currentTitle);
        tvCurrentFile = (TextView) findViewById(R.id.currentFile);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        if (mBound) initalizeDisplay();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        mHandler.removeCallbacks(UpdateProgress);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        unbindService(mConnection);
        super.onDestroy();
    }

    private void loadPlaylist(){
        Log.i(TAG, "Load");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        rootDirectory = sharedPreferences.getString("rootDirectory", null);
        if (rootDirectory == null){
            // TODO Prompt user to choose root directory
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("rootDirectory", "/mp3/AudioBooks/");
            editor.apply();
            rootDirectory = "/mp3/AudioBooks/";
        }
        File home = new File(Environment.getExternalStorageDirectory().getPath() + rootDirectory);
        final ArrayList<CharSequence> folders = new ArrayList<>();
        for (File file: home.listFiles()){
            if (file.isDirectory()){
                folders.add(file.getName());
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Folder");
        builder.setItems(folders.toArray(new CharSequence[folders.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File book = new File(Environment.getExternalStorageDirectory().getPath() + rootDirectory + "/" + folders.get(which) + "/");

                boolean pExists = false;
                Playlist playlist = null;
                for (File file : book.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".txt");
                    }
                })) {
                    if (file.getName().startsWith(folders.get(which).toString())) {
                        try {
                            FileInputStream fileInputStream = new FileInputStream(file);
                            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                            playlist = (Playlist)objectInputStream.readObject();
                            pExists = true;
                            objectInputStream.close();
                            fileInputStream.close();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        break;
                    }
                }
                if (!pExists) {
                    ArrayList<String> tracks = new ArrayList<>();
                    for (File file : book.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".mp3") || name.endsWith(".m4a");
                        }
                    })) {
                        tracks.add(file.getName());
                    }
                    playlist = new Playlist(book, tracks);
                }
                mService.playPlaylist(playlist);
                initalizeDisplay();
            }
        });
        builder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_addPlaylist: {
                loadPlaylist();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initalizeDisplay() {
        if (mService.isPlaying()){
            playpauseButton.setImageResource(R.drawable.pause);
        } else {
            playpauseButton.setImageResource(R.drawable.play);
        }
        playpauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mService.isPlaying()) {
                    mService.pause();
                    playpauseButton.setImageResource(R.drawable.play);
                } else {
                    mService.start();
                    playpauseButton.setImageResource(R.drawable.pause);
                }
            }
        });

        tvCurrentPlaylist.setText(mService.getPlaylistName());
        tvCurrentFile.setText(mService.getTrackName());

        updateTimers(tvEndTrackTime, mService.getDuration());
        updateTimers(tvEndListTime, mService.getTotalTime());
        updateTimers(tvCurrentTrackTime, 0);
        updateTimers(tvCurrentListTime, 0);
        updateTimers(tvTimerTime, 0);

        mTrackSeekBar.setMax(mService.getDuration());
        mTrackSeekBar.setProgress(0);
        mTrackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            boolean wasPlaying = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mService.isPlaying()){
                        wasPlaying = true;
                        mService.pause();
                    }
                    if (progress < seekBar.getMax() - 500) mService.seekTo(progress);
                    else mService.seekTo(mService.getDuration() - 500);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (wasPlaying) mService.start();
            }
        });

        mListSeekBar.setMax(mService.getTotalTime());
        mListSeekBar.setProgress(0);
        mListSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        mTimerSeekbar.setMax(3600000);
        mTimerSeekbar.setProgress(0);
        mTimerSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            int progress;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    this.progress = progress;
                    updateTimers(tvTimerTime, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mService.stopTimer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar){
                if (progress != 0) mService.setTimer(progress);
                else mService.stopTimer();
            }
        });

        SeekBar mPlaySpeedBar = (SeekBar) findViewById(R.id.playSpeedBar);
        mPlaySpeedBar.setMax(300);
        mPlaySpeedBar.setProgress((int)((mService.getSpeed() - .5f) * 100));
        tvPlaySpeed.setText(String.format("%.1fx", mService.getSpeed()));
        mPlaySpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mService.changeSpeed(progress / 100f + .5f);
                    tvPlaySpeed.setText(String.format("%.1fx", progress / 100f + .5f));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mHandler.postDelayed(UpdateProgress, 100);
    }

    private Runnable UpdateProgress = new Runnable() {
        @Override
        public void run() {
            int currentTrackTime = mService.getCurrentPosition();
            int currentListTime = mService.getElapsedTime();
            mTrackSeekBar.setProgress(currentTrackTime);
            mListSeekBar.setProgress(currentListTime);
            if (mService.isTimerRunning()) {
                mTimerSeekbar.setProgress((int) mService.getTimer());
                updateTimers(tvTimerTime, (int) mService.getTimer());
            } else if (!mService.isPlaying()) {
                playpauseButton.setImageResource(R.drawable.play);
            }
            updateTimers(tvCurrentTrackTime, currentTrackTime);
            updateTimers(tvCurrentListTime, currentListTime);

            if (mService.trackCompleted()) {
                updateTimers(tvEndTrackTime, mService.getDuration());
                mTrackSeekBar.setMax(mService.getDuration());
                tvCurrentFile.setText(mService.getTrackName());
            }

            mHandler.postDelayed(this, 100);
        }
    };


    private void updateTimers(TextView tv, int time){
        if (time >= 3600000) {
            tv.setText(String.format("%d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(time),
                    TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                    TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
        } else {
            tv.setText(String.format("%d:%02d", TimeUnit.MILLISECONDS.toMinutes(time),
                    TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
        }
    }


}
