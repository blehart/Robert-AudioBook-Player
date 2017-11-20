package com.example.robert.robertaudioplayer;

import android.media.MediaMetadataRetriever;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

class Playlist implements Serializable {

    private static final String TAG = "Playlist";
    private String playlistPath;
    private String playlistName;
    private int track = 0;
    private int trackPosition;
    private int totalTime;
    private int[] elapsedTime;
    private ArrayList<String> tracks;

    Playlist(File folder, ArrayList<String> list){
        playlistName = folder.getName();
        playlistPath = folder.getPath();
        tracks = list;
        track = 0;
        elapsedTime = new int[list.size()];
        trackPosition =  0;
        MediaMetadataRetriever meta = new MediaMetadataRetriever();
        for (int i = 0; i < list.size(); i++){
            elapsedTime[i] = totalTime;
            meta.setDataSource(playlistPath + "/" + list.get(i));
            totalTime += Integer.parseInt(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        }
    }

    String next(){
        track++;
        return jumpTo(track);
    }

    String current(){
        return jumpTo(track);
    }

    String jumpTo(int track){
        this.track = track;
        if (track >= tracks.size()){
            return null;
        }
        return playlistPath + "/" + tracks.get(track);
    }

    int getTrackPosition(){
        return trackPosition;
    }

    int getElapsedTime(int track){
        return elapsedTime[track];
    }

    int getTrack(){
        return track;
    }

    int getTotalTime() {
        return totalTime;
    }

    String getPlaylistName(){
        return playlistName;
    }

    String getTrackName(){
        return tracks.get(track);
    }

    void save(int currentPosition){
        Log.i(TAG, "Save");
        trackPosition = currentPosition;
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(playlistPath + "/" + playlistName + ".txt");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.close();
            objectOutputStream.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}

