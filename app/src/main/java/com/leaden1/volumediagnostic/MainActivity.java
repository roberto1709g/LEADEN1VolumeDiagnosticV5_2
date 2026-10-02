package com.leaden1.volumediagnostic;

import android.app.Activity;
import android.content.*;
import android.media.AudioManager;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.*;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private AudioManager audioManager;
    private MediaSessionManager mediaSessionManager;
    private Handler handler;
    private TextView statusView, mediaView, volumeView, keyView, changeView, historyView;
    private int lastVolume = -1;
    private boolean receiverRegistered = false;
    private final ArrayList<String> history = new ArrayList<>();

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            controllers -> runOnUiThread(() -> updateSessions(controllers));

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            if (state != null) {
                log("MEDIA_STATE_" + stateToString(state.getState()));
                updateMediaState(stateToString(state.getState()));
            }
        }
        @Override public void onSessionDestroyed() {
            log("MEDIA_SESSION_DESTROYED");
            refreshSessions();
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
            if (stream == AudioManager.STREAM_MUSIC || stream == -1) {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                log("VOLUME_BROADCAST " + lastVolume + " -> " + current);
                showVolumeChange("BROADCAST " + lastVolume + " -> " + current);
                lastVolume = current;
                updateVolume();
            }
        }
    };

    private final Runnable volumePoller = new Runnable() {
        @Override public void run() {
            if (audioManager != null) {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (lastVolume != -1 && current != lastVolume) {
                    log("VOLUME_POLL " + lastVolume + " -> " + current);
                    showVolumeChange("POLL " + lastVolume + " -> " + current);
                }
                lastVolume = current;
                updateVolume();
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        mediaSessionManager = (MediaSessionManager)getSystemService(Context.MEDIA_SESSION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        buildUi();
        lastVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        log("APP_INICIADA_V5_2");
        registerVolumeReceiver();
        handler.post(volumePoller);
        refreshSessions();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,32);

        statusView = makeText("● INICIALIZANDO");
        mediaView = makeText("MEDIA SESSION: —");
        volumeView = makeText("STREAM_MUSIC: —");
        keyView = makeText("ÚLTIMO KEY EVENT: —");
        changeView = makeText("ÚLTIMO CAMBIO: —");
        historyView = makeText("HISTORIAL\n");

        Button access = new Button(this);
        access.setText("ABRIR ACCESO A NOTIFICACIONES");
        access.setOnClickListener(v -> openNotificationAccess());

        Button update = new Button(this);
        update.setText("ACTUALIZAR SESIONES");
        update.setOnClickListener(v -> refreshSessions());

        Button clear = new Button(this);
        clear.setText("LIMPIAR HISTORIAL");
        clear.setOnClickListener(v -> { history.clear(); historyView.setText("HISTORIAL\n"); });

        root.addView(statusView); root.addView(mediaView); root.addView(volumeView);
        root.addView(keyView); root.addView(changeView);
        root.addView(access); root.addView(update); root.addView(clear);
        root.addView(historyView);
        setContentView(root);
    }

    private TextView makeText(String text) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(16); v.setPadding(0,12,0,12);
        return v;
    }

    private void registerVolumeReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction("android.media.STREAM_VOLUME_CHANGED_ACTION");
        try {
            ContextCompat.registerReceiver(this, volumeReceiver, filter,
                    ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered = true;
            log("REGISTER_RECEIVER_OK");
        } catch (Exception e) {
            log("REGISTER_RECEIVER_ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
        refreshSessions();
    }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        if (handler != null) handler.removeCallbacks(volumePoller);
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            String name = KeyEvent.keyCodeToString(event.getKeyCode());
            log("KEY_DOWN " + name + " (" + event.getKeyCode() + ")");
            keyView.setText("ÚLTIMO KEY EVENT: " + name + " (" + event.getKeyCode() + ")");
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateStatus() {
        statusView.setText(MediaNotificationListener.isConnected()
                ? "● MONITOR ACTIVO" : "● ESPERANDO CONEXIÓN DEL LISTENER");
    }

    private ComponentName getListenerComponent() {
        return new ComponentName(this, MediaNotificationListener.class);
    }

    private void refreshSessions() {
        updateStatus();
        if (!MediaNotificationListener.isConnected()) {
            mediaView.setText("MEDIA SESSION: ACCESO NO DISPONIBLE\nActiva el acceso de notificaciones y vuelve a entrar.");
            return;
        }
        try {
            List<MediaController> sessions =
                    mediaSessionManager.getActiveSessions(getListenerComponent());
            updateSessions(sessions);
            log("SESIONES_ENCONTRADAS=" + sessions.size());
        } catch (SecurityException e) {
            mediaView.setText("MEDIA SESSION: SECURITY_EXCEPTION\n" + e.getMessage());
            log("SECURITY_EXCEPTION: " + e.getMessage());
        } catch (Exception e) {
            mediaView.setText("MEDIA SESSION: ERROR\n" + e.getMessage());
            log("SESSION_ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void updateSessions(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            mediaView.setText("MEDIA SESSION: NINGUNA ACTIVA");
            return;
        }
        MediaController controller = sessions.get(0);
        try { controller.registerCallback(mediaCallback); } catch (Exception ignored) {}

        String title = "—", artist = "—";
        if (controller.getMetadata() != null) {
            CharSequence t = controller.getMetadata().getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = controller.getMetadata().getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (t != null) title = t.toString();
            if (a != null) artist = a.toString();
        }
        PlaybackState state = controller.getPlaybackState();
        String stateText = state == null ? "—" : stateToString(state.getState());

        mediaView.setText("MEDIA SESSION: " + controller.getPackageName()
                + "\nESTADO: " + stateText
                + "\nTITULO: " + title
                + "\nARTISTA: " + artist);
    }

    private void updateMediaState(String state) {
        String current = mediaView.getText().toString();
        if (current.contains("ESTADO:"))
            mediaView.setText(current.replaceAll("ESTADO: [^\\n]*", "ESTADO: " + state));
    }

    private String stateToString(int state) {
        switch(state) {
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_NONE: return "NONE";
            default: return String.valueOf(state);
        }
    }

    private void updateVolume() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volumeView.setText("STREAM_MUSIC: " + current + " / " + max);
    }

    private void showVolumeChange(String text) {
        changeView.setText("ÚLTIMO CAMBIO: " + text);
    }

    private void openNotificationAccess() {
        try {
            log("ABRIENDO_ACCESO_NOTIFICACIONES");
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            log("ERROR_ABRIENDO_ACCESO: " + e.getMessage());
        }
    }

    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String line = time + " " + message;
        history.add(0,line);
        while(history.size() > 50) history.remove(history.size()-1);
        if(historyView != null) {
            StringBuilder sb = new StringBuilder("HISTORIAL\n");
            for(String item: history) sb.append(item).append("\n");
            historyView.setText(sb.toString());
        }
    }
}
