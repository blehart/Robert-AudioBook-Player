package com.example.robert.robertaudioplayer;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

class Playlist implements Serializable {

    private static final String TAG = "Playlist";

    // File path of this playlist's folder
    private String playlistPath;

    // Name of this playlist's folder
    private String playlistName;

    // Position in the current track, only used when saving/loading the playlist
    private int trackPosition;

    // Length of entire playlist
    private int totalTime;

    // At index 5, gives the total time of files 1-4
    private ArrayList<Integer> elapsedTime;

    // List of file names
    private ArrayList<String> tracks;

    // Index of current file in tracks
    private int track = 0;

    // Create a playlist of all the audio files in the given folder
    Playlist(File folder) {
        playlistName = folder.getName();
        playlistPath = folder.getPath();
        track = 0;
        totalTime = 0;
        elapsedTime = new ArrayList<>();
        trackPosition = 0;

        MediaMetadataRetriever meta = new MediaMetadataRetriever();
        tracks = new ArrayList<>();
        File[] files = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".m4b");
            }
        });
        Arrays.sort(files);
        for (File file : files) {
            tracks.add(file.getName());
            elapsedTime.add(totalTime);
            meta.setDataSource(file.getPath());
            totalTime += Integer.parseInt(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        }
    }

    // Return the filepath of the track at index
    String jumpTo(int index) {
        track = index;
        if (track >= tracks.size()) {
            return null;
        }
        return playlistPath + "/" + tracks.get(track);
    }

    // Return the filepath of the current track
    String current() {
        return jumpTo(track);
    }

    // Return the filepath of the next track
    String next() {
        track++;
        return jumpTo(track);
    }

    // Write this playlist instance to a file in this playlist's folder named playlistName.txt
    void save(int currentPosition) {
        Log.i(TAG, "Save");
        trackPosition = currentPosition;
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(playlistPath + "/" + playlistName + ".txt");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.close();
            objectOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Return the index of the track plays at time
    int findTrack(int time){
        for (int i = 0; i < elapsedTime.size(); i++){
            if (elapsedTime.get(i) > time) return i-1;
        }
        return elapsedTime.size() - 1;
    }

    int getTrackPosition() {
        return trackPosition;
    }

    int getElapsedTime() {
        return elapsedTime.get(track);
    }

    int getTotalTime() {
        return totalTime;
    }

    String getPlaylistName() {
        return playlistName;
    }

    String getTrackName() {
        return tracks.get(track);
    }
}

