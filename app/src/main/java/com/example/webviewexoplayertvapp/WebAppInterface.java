package com.example.webviewexoplayertvapp;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.media3.common.util.UnstableApi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WebAppInterface {
    private Context context;
    private MainActivity activity;

    public WebAppInterface(Context context) {
        this.context = context;
        if (context instanceof MainActivity) {
            this.activity = (MainActivity) context;
        }
    }

    @JavascriptInterface
    public void openVideoPlayer(final String jsonData) {
        Log.d("WebAppInterface", "Received video data: " + jsonData);
        try {
            final JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
            final String videoUrl = json.get("videoUrl").getAsString();
            final String drmLicenseUrl = json.get("drmLicenseUrl").getAsString();
            final String authToken = json.get("authToken").getAsString();
            JsonObject videoLibrary = json.get("videoLibrary").getAsJsonObject();
            Log.d("WebAppInterface", "Video Library: " + videoLibrary.toString());

            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @UnstableApi
                    @Override
                    public void run() {
                        activity.startPlayback(videoUrl, drmLicenseUrl, authToken);
                    }
                });
            }
        } catch (Exception e) {
            Log.e("WebAppInterface", "Error parsing JSON: " + e.getMessage());
        }
    }
}