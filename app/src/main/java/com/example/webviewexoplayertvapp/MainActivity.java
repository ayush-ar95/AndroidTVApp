package com.example.webviewexoplayertvapp;

import android.app.Activity;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import java.util.UUID;

public class MainActivity extends Activity {

    private WebView webView;
    private PlayerView playerView;
    private ExoPlayer exoPlayer;

    private static final UUID WIDEVINE_UUID = new UUID(-0x121074568629b532L, -0x5c37D8232ae2de13L);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidTV");

        webView.loadUrl("https://dev.toqqer.com/toqqer/static/demo-ui/");  // Keep your URL

        playerView = findViewById(R.id.player_view);
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    Log.d("ExoPlayer", "Playback ready");
                } else if (playbackState == Player.STATE_ENDED) {
                    Log.d("ExoPlayer", "Playback ended");
                    stopPlayback();
                } else if (playbackState == Player.STATE_BUFFERING) {
                    Log.d("ExoPlayer", "Buffering");
                }
            }

            public void onPlayerError(Exception error) {
                Log.e("ExoPlayer", "Error: " + error.getMessage());
                stopPlayback();
            }
        });
    }

    @UnstableApi
    public void startPlayback(String videoUrl, String licenseUrl, String authToken) {
        playerView.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl, httpDataSourceFactory);
        if (authToken != null && !authToken.isEmpty()) {
            drmCallback.setKeyRequestProperty("X-AxDRM-Message", authToken);
        }

        DrmSessionManager drmSessionManager = new DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(false)
                .build(drmCallback);

        MediaSource mediaSource = new DashMediaSource.Factory(httpDataSourceFactory)
                .setDrmSessionManagerProvider(mediaItem -> drmSessionManager)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    private void stopPlayback() {
        exoPlayer.stop();
        playerView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (playerView.getVisibility() == View.VISIBLE) {
            stopPlayback();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (playerView.getVisibility() == View.VISIBLE && exoPlayer != null) {
            return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }
}