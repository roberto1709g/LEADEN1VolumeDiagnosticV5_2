package com.leaden1.volumediagnostic;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView tvStatus, tvSession, tvStream, tvKeyEvent, tvChange, tvHistory;
    private MediaSessionManager mediaSessionManager;
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder historyLog = new StringBuilder();

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            log("MEDIA_PLAYBACK_STATE_CHANGED: " + (state == null ? "NULL" : state.getState()));
            checkSessions();
        }

        @Override
        public void onSessionDestroyed() {
            log("MEDIA_SESSION_DESTROYED");
            checkSessions();
        }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                int stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                int vol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                int prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
                String info = "STREAM=" + stream + " | VOL=" + vol + " (PREV=" + prevVol + ")";
                tvChange.setText("ÚLTIMO CAMBIO: " + info);
                log("VOLUME_CHANGED: " + info);
            }
        }
    };

    private final Runnable streamPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (audioManager != null) {
                int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                tvStream.setText("STREAM_MUSIC: " + current + " / " + max);
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 48, 32, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("LEADEN1 Volume Diagnostic V5.2\n");
        tvTitle.setTextSize(18f);

        tvStatus = new TextView(this);
        tvSession = new TextView(this);
        tvStream = new TextView(this);
        tvKeyEvent = new TextView(this);
        tvChange = new TextView(this);

        tvKeyEvent.setText("ÚLTIMO KEY EVENT: —");
        tvChange.setText("ÚLTIMO CAMBIO: —");

        Button btnPerm = new Button(this);
        btnPerm.setText("ABRIR ACCESO A NOTIFICACIONES");
        btnPerm.setOnClickListener(v -> {
            log("ABRIENDO_ACCESO_NOTIFICACIONES");
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        Button btnRefresh = new Button(this);
        btnRefresh.setText("ACTUALIZAR SESIONES");
        btnRefresh.setOnClickListener(v -> {
            log("MANUAL_REFRESH_SESIONES");
            checkSessions();
        });

        Button btnClear = new Button(this);
        btnClear.setText("LIMPIAR HISTORIAL");
        btnClear.setOnClickListener(v -> {
            historyLog.setLength(0);
            tvHistory.setText("");
        });

        TextView tvHistTitle = new TextView(this);
        tvHistTitle.setText("\nHISTORIAL");

        tvHistory = new TextView(this);
        tvHistory.setTextSize(12f);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(tvHistory);

        mainLayout.addView(tvTitle);
        mainLayout.addView(tvStatus);
        mainLayout.addView(tvSession);
        mainLayout.addView(tvStream);
        mainLayout.addView(tvKeyEvent);
        mainLayout.addView(tvChange);
        mainLayout.addView(btnPerm);
        mainLayout.addView(btnRefresh);
        mainLayout.addView(btnClear);
        mainLayout.addView(tvHistTitle);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);

        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        try {
            ContextCompat.registerReceiver(
                this,
                volumeReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            );
            log("RECEIVER_REGISTRADO_OK");
        } catch (Exception e) {
            log("REGISTER_RECEIVER_ERROR: " + e.getMessage());
        }

        log("APP_INICIADA_V5_2");
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(streamPollRunnable);
        checkSessions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(streamPollRunnable);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            String keyStr = (keyCode == KeyEvent.KEYCODE_VOLUME_UP) ? "VOL_UP" : "VOL_DOWN";
            tvKeyEvent.setText("ÚLTIMO KEY EVENT: " + keyStr);
            log("KEY_DOWN: " + keyStr);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void checkSessions() {
        try {
            List<MediaController> sessions = mediaSessionManager.getActiveSessions(null);
            tvStatus.setText("● ACCESO A NOTIFICACIONES ACTIVO\n");
            updateSessions(sessions);
        } catch (SecurityException e) {
            tvStatus.setText("● ACCESO A NOTIFICACIONES NO ACTIVO\n");
            tvSession.setText("MEDIA SESSION: ACCESO NO DISPONIBLE\nActiva el acceso de notificaciones.\n");
        } catch (Exception e) {
            tvSession.setText("MEDIA SESSION: ERROR " + e.getMessage() + "\n");
        }
    }

    private void updateSessions(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            tvSession.setText("MEDIA SESSION: — (Sin reproducción activa)\n");
            return;
        }
        MediaController controller = sessions.get(0);
        try {
            controller.registerCallback(mediaCallback);
            String pkg = controller.getPackageName();
            PlaybackState state = controller.getPlaybackState();
            String title = "—";
            if (controller.getMetadata() != null) {
                CharSequence t = controller.getMetadata().getText(MediaMetadata.METADATA_KEY_TITLE);
                if (t != null) title = t.toString();
            }
            tvSession.setText("MEDIA SESSION: " + pkg + "\nTÍTULO: " + title + "\nESTADO: " + (state == null ? "—" : state.getState()) + "\n");
        } catch (Exception e) {
            tvSession.setText("MEDIA SESSION: ERROR " + e.getMessage() + "\n");
        }
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        historyLog.insert(0, time + "  " + msg + "\n");
        if (tvHistory != null) {
            tvHistory.setText(historyLog.toString());
        }
    }
}
