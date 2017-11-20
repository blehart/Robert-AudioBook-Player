package com.example.robert.robertaudioplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
    private final int REQUEST_EXTERNAL_STORAGE = 100;
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
            loadPlaylist(true);
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    /* Attach all the views.
       If we have external storage permissions, start the PlayerService,
       otherwise ask for those permissions. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (this.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
        else {
            Intent intent = new Intent(this, PlayerService.class);
            bindService(intent, mConnection, BIND_AUTO_CREATE);
        }
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

    /* If the external storage permissions are granted, start the PlayerService,
       otherwise exit the app. */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode){
            case REQUEST_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(this, PlayerService.class);
                    bindService(intent, mConnection, BIND_AUTO_CREATE);
                }
                else {
                    System.exit(0);
                }
        }
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

    /* Check for (or create) a rootDirectory preference.
       If we want to autoload the previously used folder and one exists, do so.
       Otherwise, prompt the user to select a folder in the root directory, then load that folder and
       save it as the previously used folder. */
    private void loadPlaylist(boolean usePreviousFolder){
        Log.i(TAG, "Load");
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        rootDirectory = sharedPreferences.getString("rootDirectory", null);
        if (rootDirectory == null){
            // TODO Prompt user to choose root directory
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("rootDirectory", "/mp3/AudioBooks/");
            editor.apply();
            rootDirectory = "/mp3/AudioBooks/";
        }
        String previousFolder;
        if (usePreviousFolder && (previousFolder = sharedPreferences.getString("previousFolder", null)) != null){
            loadPlaylist2(previousFolder);
        }
        else {
            File home = new File(Environment.getExternalStorageDirectory().getPath() + rootDirectory);
            final ArrayList<CharSequence> folders = new ArrayList<>();
            for (File file : home.listFiles()) {
                if (file.isDirectory()) {
                    folders.add(file.getName());
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Folder");
            builder.setItems(folders.toArray(new CharSequence[folders.size()]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    loadPlaylist2(folders.get(which).toString());
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("previousFolder", folders.get(which).toString());
                    editor.apply();
                }
            });
            builder.show();
        }
    }

    /* If the given folder contains a playlist file, load it,
       otherwise create a new playlist instance.
       Send the playlist to the PlayerService and then call initalizeDisplay() */
    private void loadPlaylist2 (String folderName){
        File folder = new File(Environment.getExternalStorageDirectory().getPath() + rootDirectory + "/" + folderName + "/");
        boolean pExists = false;
        Playlist playlist = null;
        for (File file : folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt");
            }
        })) {
            if (file.getName().startsWith(folderName)) {
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
            playlist = new Playlist(folder);
        }
        mService.playPlaylist(playlist);
        initalizeDisplay();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_selectFolder: {
                if (mService.isPlaying()) {
                    mService.pause();
                    playpauseButton.setImageResource(R.drawable.play);
                }
                loadPlaylist(false);
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
                    pause();
                } else {
                    play();
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
                        pause();
                    }
                    if (progress < seekBar.getMax() - 500) mService.seekToLocal(progress);
                    else mService.seekToLocal(mService.getDuration() - 500);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (wasPlaying) {
                    play();
                }
            }
        });

        mListSeekBar.setMax(mService.getTotalTime());
        mListSeekBar.setProgress(0);
        mListSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasPlaying = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mService.isPlaying()){
                        wasPlaying = true;
                        pause();
                    }
                    if (progress < seekBar.getMax() - 500) mService.seekToGlobal(progress);
                    else mService.seekToGlobal(mService.getDuration() - 500);
                    tvCurrentFile.setText(mService.getTrackName());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (wasPlaying){
                    play();
                }
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
                    updateTimers(tvTimerTime, this.progress);
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
        mPlaySpeedBar.setMax(350);
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

    private void play(){
        mService.start();
        playpauseButton.setImageResource(R.drawable.pause);
    }

    private void pause(){
        mService.pause();
        playpauseButton.setImageResource(R.drawable.play);
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
