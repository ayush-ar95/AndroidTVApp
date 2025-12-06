    package com.example.webviewexoplayertvapp;

    import static android.media.MediaDrm.*;

    import android.app.Activity;
    import android.net.Uri;
    import android.os.Build;
    import android.os.Bundle;
    import android.util.Base64;
    import android.util.Log;
    import android.util.Pair;
    import android.view.Gravity;
    import android.view.KeyEvent;
    import android.view.View;
    import android.webkit.JavascriptInterface;
    import android.webkit.WebChromeClient;
    import android.webkit.WebSettings;
    import android.webkit.WebView;
    import android.webkit.WebViewClient;

    import androidx.annotation.OptIn;
    import androidx.annotation.RequiresApi;
    import androidx.media3.common.DrmInitData;
    import androidx.media3.common.Format;
    import androidx.media3.common.MediaItem;
    import androidx.media3.common.MimeTypes;
    import androidx.media3.common.Player;
    import androidx.media3.common.TrackGroup;
    import android.media.MediaDrm;
    import androidx.media3.common.TrackSelectionParameters;
    import androidx.media3.common.Tracks;
    import androidx.media3.common.util.UnstableApi;
    import androidx.media3.datasource.DefaultHttpDataSource;
    import androidx.media3.exoplayer.ExoPlaybackException;
    import androidx.media3.exoplayer.ExoPlayer;
    import androidx.media3.exoplayer.dash.DashMediaSource;
    import androidx.media3.exoplayer.dash.DashUtil;
    import androidx.media3.exoplayer.dash.manifest.DashManifest;
    import androidx.media3.exoplayer.dash.manifest.Period;
    import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
    import androidx.media3.exoplayer.drm.DrmSession;
    import androidx.media3.exoplayer.drm.DrmSessionEventListener;
    import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
    import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
    import androidx.media3.exoplayer.drm.OfflineLicenseHelper;
    import androidx.media3.exoplayer.drm.KeysExpiredException;
    import androidx.media3.exoplayer.source.MediaSource;
    import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
    import androidx.media3.ui.AspectRatioFrameLayout;
    import androidx.media3.ui.PlayerView;
    import androidx.security.crypto.EncryptedSharedPreferences;
    import androidx.security.crypto.MasterKeys;

    import com.google.common.collect.ImmutableList;
    import com.google.gson.JsonArray;
    import com.google.gson.JsonElement;
    import com.google.gson.JsonObject;

    import java.lang.reflect.Field;
    import java.util.HashMap;
    import java.util.Map;
    import java.util.UUID;
    import android.os.Build.VERSION_CODES;
    import android.widget.PopupWindow;

    public class MainActivity extends Activity {

        private static final String TAG = "MainActivity";
        private static final String DRM_TAG = "DRM_LICENSE";
        private static final String SECURITY_LEVEL_L1 = "L1";
        private static final String SECURITY_LEVEL_L3 = "L3";
        private WebView webView;
        private PlayerView playerView;
        private ExoPlayer exoPlayer;
        private android.content.SharedPreferences drmPrefs;
        @UnstableApi
        private DefaultTrackSelector trackSelector;
        private static final UUID WIDEVINE_UUID = new UUID(-0x121074568629b532L, -0x5c37D8232ae2de13L);
        private static final String DRM_PREFS_NAME = "drm_prefs";
        private static final String DRM_KEY_SET_ID = "widevine_key_set_id";

        private boolean isRetryingPlayback = false;
        private String currentVideoUrl;
        private String currentLicenseUrl;
        private String currentAuthToken;
        private JsonObject currentVideoLibrary;
        private boolean hasSubtitles = false;
        private float currentVolume = 1.0f;

        interface DrmInitDataCallback {
            @UnstableApi
            void onSuccess(DrmInitData drmInitData);
            void onError(Exception e);
        }

        @UnstableApi
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            Log.d(TAG, "onCreate: Initializing MainActivity");

            // Initialize encrypted shared preferences for secure DRM storage
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                drmPrefs = EncryptedSharedPreferences.create(
                        DRM_PREFS_NAME,
                        masterKeyAlias,
                        this,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                Log.d(DRM_TAG, "EncryptedSharedPreferences initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences", e);
            }

            // Setup WebView
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
            webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
            webView.loadUrl("https://dev.toqqer.com/toqqer/static/toqqer-ui/");
//    webView.loadUrl("https://dev.toqqer.com/toqqer/static/demo-ui/");
//    webView.loadUrl("https://toqqer.com/ui/@LimpopoTv/f");

            // Setup ExoPlayer
            playerView = findViewById(R.id.player_view);
            trackSelector = new DefaultTrackSelector(this);
            exoPlayer = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .build();
            playerView.setPlayer(exoPlayer);

            // Setup player event listener - FIXED for Media3 1.4.1
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "Playback state: " + getPlaybackStateName(playbackState));
                    if (playbackState == Player.STATE_READY) {
                        playerView.showController();
                        updateSubtitleMenu();
                    }
                }

                @UnstableApi @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Log.e(TAG, "Player error: " + error.getMessage(), error);
                    if (isDrmExpirationError(error) && !isRetryingPlayback) {
                        Log.w(DRM_TAG, "DRM expired - recovering");
                        clearCachedKeySetId();
                        isRetryingPlayback = true;
                        // CHANGE: Added currentVideoLibrary as the 4th parameter
                        startPlayback(currentVideoUrl, currentLicenseUrl, currentAuthToken, currentVideoLibrary);
                    } else {
                        stopPlayback();
                    }
                }

                @Override
                public void onTracksChanged(Tracks tracks) {
                    hasSubtitles = false;
                    for (Tracks.Group group : tracks.getGroups()) {
                        if (group.getType() == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                            hasSubtitles = true;
                            break;
                        }
                    }
                    updateSubtitleMenu();
                }
            });

            // Add dynamic timeout adjustment for settings submenu and reposition popup above
            androidx.media3.ui.PlayerControlView controller = playerView.findViewById(androidx.media3.ui.R.id.exo_controller);
            if (controller != null) {
                android.widget.ImageButton settingsButton = controller.findViewById(androidx.media3.ui.R.id.exo_settings);
                if (settingsButton != null) {
                    try {
                        // Use reflection to get existing OnClickListener
                        Field listenerInfoField = View.class.getDeclaredField("mListenerInfo");
                        listenerInfoField.setAccessible(true);
                        Object listenerInfo = listenerInfoField.get(settingsButton);
                        View.OnClickListener originalClickListener = null;
                        if (listenerInfo != null) {
                            Field onClickField = listenerInfo.getClass().getDeclaredField("mOnClickListener");
                            onClickField.setAccessible(true);
                            originalClickListener = (View.OnClickListener) onClickField.get(listenerInfo);
                        }

                        final View.OnClickListener finalOriginalClick = originalClickListener;
                        settingsButton.setOnClickListener(v -> {
                            try {
                                Field field = androidx.media3.ui.PlayerControlView.class.getDeclaredField("settingsWindow");
                                field.setAccessible(true);
                                PopupWindow popup = (PopupWindow) field.get(controller);
                                if (popup != null) {
                                    popup.setHeight(0);
                                    popup.getContentView().setVisibility(View.INVISIBLE);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error setting popup initial state", e);
                            }

                            if (finalOriginalClick != null) {
                                finalOriginalClick.onClick(v);
                            }

                            try {
                                Field field = androidx.media3.ui.PlayerControlView.class.getDeclaredField("settingsWindow");
                                field.setAccessible(true);
                                PopupWindow popup = (PopupWindow) field.get(controller);
                                if (popup != null) {
                                    // Use reflection to get existing OnDismissListener
                                    Field dismissField = PopupWindow.class.getDeclaredField("mOnDismissListener");
                                    dismissField.setAccessible(true);
                                    PopupWindow.OnDismissListener originalDismiss = (PopupWindow.OnDismissListener) dismissField.get(popup);

                                    final long[] lastDismissTime = {0};
                                    final boolean[] isRepositioning = {false};

                                    popup.setOnDismissListener(() -> {
                                        long currentTime = System.currentTimeMillis();
                                        if (currentTime - lastDismissTime[0] < 200) {
                                            return;
                                        }
                                        lastDismissTime[0] = currentTime;

                                        if (isRepositioning[0]) {
                                            isRepositioning[0] = false;
                                            return;
                                        }
                                        if (originalDismiss != null) {
                                            originalDismiss.onDismiss();
                                        }
                                        controller.setShowTimeoutMs(3000);
                                        controller.postDelayed(() -> {
                                            if (popup.isShowing()) {
                                                controller.setShowTimeoutMs(0);
                                                // Reposition sub popup
                                                popup.getContentView().post(() -> {
                                                    if (popup.isShowing()) {
                                                        isRepositioning[0] = true;
                                                        popup.dismiss();
                                                        popup.getContentView().measure(
                                                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                                                        int popupWidth = popup.getContentView().getMeasuredWidth();
                                                        int popupHeight = popup.getContentView().getMeasuredHeight();
                                                        popup.setWidth(popupWidth);
                                                        popup.setHeight(popupHeight);
                                                        int[] location = new int[2];
                                                        settingsButton.getLocationOnScreen(location);
                                                        int extraPadding = 20; // Adjust this value to move it higher
                                                        int x = location[0] + settingsButton.getWidth() - popupWidth; // Right-aligned
                                                        int y = location[1] - popupHeight - extraPadding; // Above the button with extra padding
                                                        popup.showAtLocation(playerView, Gravity.NO_GRAVITY, x, y);
                                                        popup.getContentView().setVisibility(View.VISIBLE);
                                                    }
                                                });
                                            }
                                        }, 100);
                                    });
                                    controller.setShowTimeoutMs(0);

                                    // Reposition main popup
                                    popup.getContentView().post(() -> {
                                        if (popup.isShowing()) {
                                            isRepositioning[0] = true;
                                            popup.dismiss();
                                            popup.getContentView().measure(
                                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                                            int popupWidth = popup.getContentView().getMeasuredWidth();
                                            int popupHeight = popup.getContentView().getMeasuredHeight();
                                            popup.setWidth(popupWidth);
                                            popup.setHeight(popupHeight);
                                            int[] location = new int[2];
                                            settingsButton.getLocationOnScreen(location);
                                            int extraPadding = 20; // Adjust this value to move it higher
                                            int x = location[0] + settingsButton.getWidth() - popupWidth; // Right-aligned
                                            int y = location[1] - popupHeight - extraPadding; // Above the button with extra padding
                                            popup.showAtLocation(playerView, Gravity.NO_GRAVITY, x, y);
                                            popup.getContentView().setVisibility(View.VISIBLE);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error adjusting controller timeout or position", e);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting up settings button listener", e);
                    }
                }
            }

            Log.d(TAG, "onCreate: Complete");
        }


        @UnstableApi
        public void startPlayback(String videoUrl, String licenseUrl, String authToken, JsonObject videoLibrary) {
            Log.d(TAG, "Starting Playback - URL: " + videoUrl);
            currentVideoUrl = videoUrl;
            currentLicenseUrl = licenseUrl;
            currentAuthToken = authToken;
            currentVideoLibrary = videoLibrary;  // CHANGE 1: Store the videoLibrary
            isRetryingPlayback = false;

            try {
                // Widevine level check
                String securityLevel = getWidevineSecurityLevel();
                Log.d(DRM_TAG, "Widevine level: " + securityLevel);
                if (securityLevel.equals(SECURITY_LEVEL_L3)) {
                    Log.w(DRM_TAG, "L3 fallback - limiting to SD");
                    TrackSelectionParameters params = trackSelector.getParameters()
                            .buildUpon()
                            .setMaxVideoSizeSd()
                            .build();
                    trackSelector.setParameters(params);
                }

                // Pause WebView
                webView.onPause();
                webView.setVisibility(View.INVISIBLE);
                playerView.setVisibility(View.VISIBLE);

                // Parse and set aspect ratio
                parseAndSetAspectRatio();

                // CHANGE 2: Added the missing playback logic
                // Check if we have a cached license
                byte[] keySetId = getCachedKeySetId();

                if (keySetId != null) {
                    // We have a cached license, validate and use it
                    Log.d(DRM_TAG, "Found cached keySetId, validating...");
                    handleCachedLicense(videoUrl, licenseUrl, authToken, keySetId);
                } else {
                    // No cached license, acquire a new one
                    Log.d(DRM_TAG, "No cached keySetId found, acquiring new license...");
                    acquireNewLicense(videoUrl, licenseUrl, authToken);
                }

            } catch (Exception e) {
                Log.e(TAG, "Playback start error", e);
                stopPlayback();
            }
        }


        @UnstableApi
        @OptIn(markerClass = UnstableApi.class)
        private String getWidevineSecurityLevel() {
            try {
                FrameworkMediaDrm drm = FrameworkMediaDrm.newInstance(WIDEVINE_UUID);
                String level;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    level = drm.getPropertyString("securityLevel");
                } else {
                    level = SECURITY_LEVEL_L3;  // Safe fallback for older devices
                }
                drm.release();
                return level;
            } catch (Exception e) {
                Log.e(DRM_TAG, "Failed to get security level", e);
                return SECURITY_LEVEL_L3;
            }
        }

        @UnstableApi
        private void parseAndSetAspectRatio() {
            if (currentVideoLibrary != null) {
                JsonArray properties = currentVideoLibrary.getAsJsonArray("properties");
                for (JsonElement prop : properties) {
                    JsonObject obj = prop.getAsJsonObject();
                    if (obj.get("name").getAsString().equals("scale")) {
                        String scale = obj.get("value").getAsString();  // e.g., "1280,720,H"
                        if (scale.contains("H")) {
                            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                        } else if (scale.contains("V")) {
                            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);  // Example for vertical
                        }
                        break;
                    }
                }
            }
        }

        @UnstableApi
        private void updateSubtitleMenu() {
            if (!hasSubtitles) {
                TrackSelectionParameters params = trackSelector.getParameters()
                        .buildUpon()
                        .setDisabledTextTrackSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build();
                trackSelector.setParameters(params);
            }
        }

        @UnstableApi
        private void handleCachedLicense(String videoUrl, String licenseUrl, String authToken, byte[] keySetId) {
            OfflineLicenseHelper helper = buildOfflineLicenseHelper(licenseUrl, authToken);

            try {
                // Check remaining license duration
                Pair<Long, Long> durations = helper.getLicenseDurationRemainingSec(keySetId);
                long licenseDuration = durations.first;
                long playbackDuration = durations.second;

                Log.d(DRM_TAG, "License duration remaining: " + licenseDuration + " seconds");
                Log.d(DRM_TAG, "Playback duration remaining: " + playbackDuration + " seconds");

                if (playbackDuration <= 0) {
                    Log.w(DRM_TAG, "License expired, attempting renewal...");
                    try {
                        keySetId = helper.renewLicense(keySetId);
                        cacheKeySetId(keySetId);
                        Log.d(DRM_TAG, "✓ License renewed successfully");
                        byte[] finalKeySetId = keySetId;
                        runOnUiThread(() -> buildAndPlayMediaSource(videoUrl, licenseUrl, authToken, finalKeySetId));
                    } catch (Exception renewError) {
                        Log.e(DRM_TAG, "✗ License renewal failed, acquiring new license", renewError);
                        helper.releaseLicense(keySetId);
                        clearCachedKeySetId();
                        acquireNewLicense(videoUrl, licenseUrl, authToken);
                    }
                } else {
                    Log.d(DRM_TAG, "✓ Using valid cached license (playback time: " + playbackDuration + "s)");
                    byte[] finalKeySetId1 = keySetId;
                    runOnUiThread(() -> buildAndPlayMediaSource(videoUrl, licenseUrl, authToken, finalKeySetId1));
                }
            } catch (Exception e) {
                Log.e(DRM_TAG, "Error checking cached license validity", e);
                clearCachedKeySetId();
                acquireNewLicense(videoUrl, licenseUrl, authToken);
            } finally {
                helper.release();
            }
        }

        @UnstableApi
        private void acquireNewLicense(String videoUrl, String licenseUrl, String authToken) {
            Log.d(DRM_TAG, "Acquiring new offline license...");

            // Get DRM init data from manifest
            getDrmInitDataFromManifest(videoUrl, authToken, new DrmInitDataCallback() {
                @Override
                public void onSuccess(DrmInitData drmInitData) {
                    Log.d(DRM_TAG, "DrmInitData retrieved successfully, acquiring license...");
                    OfflineLicenseHelper helper = buildOfflineLicenseHelper(licenseUrl, authToken);

                    try {
                        // FIXED: Media3 1.4.1 downloadLicense() requires Format, not DrmInitData
                        // Create a Format object that contains the DRM init data
                        Format format = new Format.Builder()
                                .setDrmInitData(drmInitData)
                                .build();

                        Log.d(DRM_TAG, "Format created with DRM init data, downloading license...");
                        byte[] newKeySetId = helper.downloadLicense(format);
                        cacheKeySetId(newKeySetId);
                        Log.d(DRM_TAG, "✓ New license acquired and cached successfully");
                        Log.d(DRM_TAG, "KeySetId length: " + newKeySetId.length + " bytes");

                        runOnUiThread(() -> buildAndPlayMediaSource(videoUrl, licenseUrl, authToken, newKeySetId));
                    } catch (Exception e) {
                        Log.e(DRM_TAG, "✗ Failed to acquire license, falling back to online mode", e);
                        runOnUiThread(() -> buildAndPlayMediaSource(videoUrl, licenseUrl, authToken, null));
                    } finally {
                        helper.release();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(DRM_TAG, "✗ Failed to get DrmInitData, using online playback", e);
                    runOnUiThread(() -> buildAndPlayMediaSource(videoUrl, licenseUrl, authToken, null));
                }
            });
        }


        @UnstableApi
        private void getDrmInitDataFromManifest(String videoUrl, String authToken, DrmInitDataCallback callback) {
            Log.d(DRM_TAG, "Fetching DRM init data from manifest: " + videoUrl);

            new Thread(() -> {
                DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
                androidx.media3.datasource.DataSource dataSource = httpDataSourceFactory.createDataSource();

                if (authToken != null && !authToken.isEmpty()) {
                    ((DefaultHttpDataSource) dataSource).setRequestProperty("X-AxDRM-Message", authToken);
                    Log.d(DRM_TAG, "Added X-AxDRM-Message header to manifest request");
                }

                try {
                    // Load the DASH manifest
                    DashManifest manifest = DashUtil.loadManifest(dataSource, Uri.parse(videoUrl));
                    Log.d(DRM_TAG, "Manifest loaded successfully");

                    // Get the first period
                    Period period = manifest.getPeriod(0);

                    if (period == null) {
                        throw new Exception("No periods found in manifest");
                    }

                    // Load format with DRM init data from the period
                    Format format = DashUtil.loadFormatWithDrmInitData(dataSource, period);
                    Log.d(DRM_TAG, "Format loaded from period");

                    if (format == null) {
                        throw new Exception("No format found in period");
                    }

                    if (format.drmInitData != null) {
                        Log.d(DRM_TAG, "✓ Found DRM init data (scheme count: " + format.drmInitData.schemeDataCount + ")");
                        callback.onSuccess(format.drmInitData);
                    } else {
                        Log.e(DRM_TAG, "✗ No DRM init data found even after loading init segment");
                        callback.onError(new Exception("No DRM init data found"));
                    }
                } catch (Exception e) {
                    Log.e(DRM_TAG, "Error loading manifest or DRM init data", e);
                    callback.onError(e);
                }
            }).start();
        }

        private void addSubtitlesToMediaItem(MediaItem.Builder builder) {
            ImmutableList.Builder<MediaItem.SubtitleConfiguration> subsBuilder = ImmutableList.builder();
            // Add existing if any (though usually empty at this point)
            // Since builder doesn't have getter, assume starting empty or track in code
            if (currentVideoLibrary != null) {
                JsonArray properties = currentVideoLibrary.getAsJsonArray("properties");
                for (JsonElement prop : properties) {
                    JsonObject obj = prop.getAsJsonObject();
                    if (obj.get("name").getAsString().equals("subtitles")) {
                        String value = obj.get("value").getAsString();
                        String[] tracks = value.split(";");
                        for (String track : tracks) {
                            String[] parts = track.split(",");
                            if (parts.length >= 2) {
                                subsBuilder.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(parts[0]))
                                        .setMimeType(MimeTypes.TEXT_VTT)
                                        .setLanguage(parts[1])
                                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                        .build());
                            }
                        }
                        hasSubtitles = true;
                        break;
                    }
                }
            }
            builder.setSubtitleConfigurations(subsBuilder.build());
        }


        @UnstableApi
        private void buildAndPlayMediaSource(String videoUrl, String licenseUrl, String authToken, byte[] keySetId) {
            Log.d(TAG, "Building media source...");

            if (keySetId != null) {
                Log.d(DRM_TAG, "Using OFFLINE license mode (cached keySetId)");
            } else {
                Log.d(DRM_TAG, "Using ONLINE license mode (streaming)");
            }

            // Setup license request headers
            Map<String, String> headers = new HashMap<>();
            if (authToken != null && !authToken.isEmpty()) {
                headers.put("X-AxDRM-Message", authToken);
                Log.d(DRM_TAG, "Added X-AxDRM-Message header to DRM request");
            }

            // Build DRM configuration
            MediaItem.DrmConfiguration.Builder drmBuilder = new MediaItem.DrmConfiguration.Builder(WIDEVINE_UUID)
                    .setLicenseUri(licenseUrl)
                    .setLicenseRequestHeaders(headers)
                    .setMultiSession(false);

            if (keySetId != null) {
                drmBuilder.setKeySetId(keySetId);
                Log.d(DRM_TAG, "KeySetId attached to media item");
            }

            // Build media item
            MediaItem.Builder builder = new MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setDrmConfiguration(drmBuilder.build());

            // Add subtitles if available
            addSubtitlesToMediaItem(builder);

            MediaItem mediaItem = builder.build();

            // Create media source
            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
            MediaSource mediaSource = new DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem);

            Log.d(TAG, "Media source created, preparing player...");
            exoPlayer.setMediaSource(mediaSource);
            exoPlayer.prepare();
            exoPlayer.play();

            Log.d(TAG, "✓ Playback started successfully");
        }
        @UnstableApi
        private OfflineLicenseHelper buildOfflineLicenseHelper(String licenseUrl, String authToken) {
            Log.d(DRM_TAG, "Building OfflineLicenseHelper...");

            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory();
            HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl, httpDataSourceFactory);

            if (authToken != null && !authToken.isEmpty()) {
                drmCallback.setKeyRequestProperty("X-AxDRM-Message", authToken);
                Log.d(DRM_TAG, "DRM callback configured with auth token");
            }

            DefaultDrmSessionManager drmSessionManager = new DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(false)
                    .build(drmCallback);

            return new OfflineLicenseHelper(drmSessionManager, new DrmSessionEventListener.EventDispatcher());
        }


        private byte[] getCachedKeySetId() {
            if (drmPrefs == null) {
                Log.w(DRM_TAG, "DRM preferences not initialized");
                return null;
            }

            String base64 = drmPrefs.getString(DRM_KEY_SET_ID, null);

            if (base64 != null) {
                byte[] keySetId = Base64.decode(base64, Base64.DEFAULT);
                Log.d(DRM_TAG, "Retrieved cached keySetId (" + keySetId.length + " bytes)");
                return keySetId;
            } else {
                Log.d(DRM_TAG, "No cached keySetId found in storage");
                return null;
            }
        }

        private void cacheKeySetId(byte[] keySetId) {
            if (drmPrefs == null || keySetId == null) {
                Log.w(DRM_TAG, "Cannot cache keySetId - preferences or keySetId is null");
                return;
            }

            String base64 = Base64.encodeToString(keySetId, Base64.DEFAULT);
            drmPrefs.edit().putString(DRM_KEY_SET_ID, base64).apply();
            Log.d(DRM_TAG, "✓ KeySetId cached successfully (" + keySetId.length + " bytes)");
        }

        private void clearCachedKeySetId() {
            if (drmPrefs != null) {
                drmPrefs.edit().remove(DRM_KEY_SET_ID).apply();
                Log.d(DRM_TAG, "✓ Cached keySetId cleared from storage");
            }
        }

        @UnstableApi
        private boolean isDrmExpirationError(androidx.media3.common.PlaybackException error) {
            // UPDATED: Changed from ExoPlaybackException to PlaybackException
            if (error instanceof ExoPlaybackException) {
                ExoPlaybackException exoError = (ExoPlaybackException) error;
                if (exoError.type == ExoPlaybackException.TYPE_RENDERER) {
                    Throwable cause = exoError.getCause();
                    boolean isExpired = cause instanceof DrmSession.DrmSessionException &&
                            cause.getCause() instanceof KeysExpiredException;

                    if (isExpired) {
                        Log.w(DRM_TAG, "DRM keys expired - license needs refresh");
                    }
                    return isExpired;
                }
            }
            return false;
        }

        private void stopPlayback() {
            try {
                exoPlayer.stop();
                playerView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                webView.onResume();
                hasSubtitles = false;
                isRetryingPlayback = false;
            } catch (Exception e) {
                Log.e(TAG, "Stop error", e);
            }
        }

        @Override
        public void onBackPressed() {
            if (playerView.getVisibility() == View.VISIBLE) {
                Log.d(TAG, "Back pressed - stopping video playback");
                stopPlayback();
            } else if (webView.canGoBack()) {
                Log.d(TAG, "Back pressed - navigating WebView back");
                webView.goBack();
            } else {
                Log.d(TAG, "Back pressed - exiting app");
                super.onBackPressed();
            }
        }

        @Override
        protected void onPause() {
            super.onPause();
            if (exoPlayer != null) {
                Log.d(TAG, "Activity paused - pausing playback");
                exoPlayer.pause();
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (exoPlayer != null) {
                Log.d(TAG, "Activity destroyed - releasing ExoPlayer");
                exoPlayer.release();
            }
        }

        @UnstableApi
        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (playerView.getVisibility() == View.VISIBLE && exoPlayer != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        float delta = (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) ? 0.1f : -0.1f;
                        currentVolume = Math.max(0f, Math.min(1f, currentVolume + delta));
                        exoPlayer.setVolume(currentVolume);
                        playerView.showController();
                        return true;
                    }
                    playerView.showController();  // Show on any key
                }
                return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }

        // Helper method for readable log output
        private String getPlaybackStateName(int state) {
            switch (state) {
                case Player.STATE_IDLE: return "IDLE";
                case Player.STATE_BUFFERING: return "BUFFERING";
                case Player.STATE_READY: return "READY";
                case Player.STATE_ENDED: return "ENDED";
                default: return "UNKNOWN(" + state + ")";
            }
        }

        public class NativeBridge {
            @JavascriptInterface
            public String getPlatform() {
                return "android_tv";
            }
        }
    }