package it.cardoni.orologio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Widget orologio disegnato come IMMAGINE: l'app disegna i due quadrati con il suo
 * carattere e passa al launcher una foto già pronta, così HyperOS non può sbagliare il font.
 *
 * Aggiornamento: un allarme NON-wakeup allo scoccare di ogni minuto.
 * A schermo spento il telefono non viene svegliato; alla riaccensione l'ora si aggiorna subito.
 */
public class ClockWidget extends AppWidgetProvider {

    static final String AZIONE_MINUTO = "it.cardoni.orologio.MINUTO";

    // ---- aspetto (stessi valori dell'anteprima) ----
    private static final int COLORE_QUADRATO = 0xFF0D0D0D;
    private static final int COLORE_NUMERI = 0xFFB0B0B0;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String a = intent.getAction();
        if (a == null) return;
        if (!AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)
                && !AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(a)
                && !AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(a)
                && !AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(a)
                && !AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(a)) {
            // minuto scattato, riavvio, cambio ora/fuso/lingua
            aggiornaTutti(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        aggiornaTutti(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle opzioni) {
        disegna(context, manager, id);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(intentMinuto(context));
    }

    // -------------------------------------------------------------------------

    static void aggiornaTutti(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ClockWidget.class));
        if (ids.length == 0) return;
        for (int id : ids) disegna(context, manager, id);
        programmaProssimoMinuto(context);
    }

    private static PendingIntent intentMinuto(Context context) {
        Intent i = new Intent(context, ClockWidget.class).setAction(AZIONE_MINUTO);
        return PendingIntent.getBroadcast(context, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void programmaProssimoMinuto(Context context) {
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, 1);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long quando = c.getTimeInMillis();
        PendingIntent pi = intentMinuto(context);
        // RTC (non wakeup): non sveglia il telefono a schermo spento
        boolean esatto = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (esatto) {
            am.setExact(AlarmManager.RTC, quando, pi);
        } else {
            am.setWindow(AlarmManager.RTC, quando, 10_000, pi);
        }
    }

    private static void disegna(Context context, AppWidgetManager manager, int id) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        Bundle o = manager.getAppWidgetOptions(id);
        boolean verticale = context.getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int wDp = verticale ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                            : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        int hDp = verticale ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                            : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        if (wDp <= 0) wDp = 320;
        if (hDp <= 0) hDp = 160;

        int w = Math.round(wDp * dm.density);
        int h = Math.round(hDp * dm.density);
        // tetto per non usare troppa memoria
        float ridu = Math.min(1f, 1200f / Math.max(w, h));
        w = Math.max(1, Math.round(w * ridu));
        h = Math.max(1, Math.round(h * ridu));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget);
        views.setImageViewBitmap(R.id.immagine, creaImmagine(context, w, h));

        Intent apri = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.radice, PendingIntent.getActivity(context, 0, apri,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(id, views);
    }

    /** Disegna i due quadrati: [data / ore]  [giorno / minuti]. */
    static Bitmap creaImmagine(Context context, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        Typeface fNumeri = context.getResources().getFont(R.font.montserrat_numeri);
        Typeface fTesto = context.getResources().getFont(R.font.montserrat_testo);

        Date ora = new Date();
        Locale loc = Locale.getDefault();
        String ore = new SimpleDateFormat("HH", loc).format(ora);
        String minuti = new SimpleDateFormat("mm", loc).format(ora);
        String data = new SimpleDateFormat("dd/MM/yy", loc).format(ora);
        String giorno = new SimpleDateFormat("EEEE", loc).format(ora).toLowerCase(loc);

        // i due quadrati restano quadrati e centrati nello spazio del widget
        float spazio = w * 0.045f;
        float lato = Math.min((w - spazio) / 2f, h);
        float x0 = (w - (lato * 2 + spazio)) / 2f;
        float y0 = (h - lato) / 2f;
        float raggio = lato * 0.16f;

        Paint pq = new Paint(Paint.ANTI_ALIAS_FLAG);
        pq.setColor(COLORE_QUADRATO);

        Paint pn = new Paint(Paint.ANTI_ALIAS_FLAG);
        pn.setColor(COLORE_NUMERI);
        pn.setTypeface(fNumeri);
        pn.setTextAlign(Paint.Align.CENTER);
        pn.setLetterSpacing(0.12f);
        pn.setTextSize(lato * 0.56f);

        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setColor(COLORE_NUMERI);
        pt.setTypeface(fTesto);
        pt.setTextAlign(Paint.Align.CENTER);
        pt.setTextSize(lato * 0.092f);

        String[] grandi = {ore, minuti};
        String[] piccoli = {data, giorno};

        for (int i = 0; i < 2; i++) {
            float l = x0 + i * (lato + spazio);
            RectF r = new RectF(l, y0, l + lato, y0 + lato);
            cv.drawRoundRect(r, raggio, raggio, pq);

            float cx = r.centerX();
            float cy = r.centerY();

            // numeri centrati in verticale (sulle cifre, non sulla riga del testo)
            android.graphics.Rect b = new android.graphics.Rect();
            pn.getTextBounds(grandi[i], 0, grandi[i].length(), b);
            // letterSpacing aggiunge spazio anche dopo l'ultima cifra: compenso per centrare
            cv.drawText(grandi[i], cx + pn.getTextSize() * 0.06f, cy - b.exactCenterY(), pn);

            // taglio orizzontale stile flip
            float sp = Math.max(2f, lato * 0.012f);
            cv.drawRect(r.left, cy - sp / 2f, r.right, cy + sp / 2f, pq);

            // data / giorno in alto
            cv.drawText(piccoli[i], cx, r.top + lato * 0.155f, pt);
        }
        return bmp;
    }
}
