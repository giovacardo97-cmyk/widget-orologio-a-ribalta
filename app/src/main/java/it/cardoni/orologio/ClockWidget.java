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
    private static final int COLORE_QUADRATO = 0xFF0C0C0C;
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

    // ---------------- disegno stile "segmenti" ----------------

    private static final int COLORE_SPENTO = 0xFF1A1A1A;   // segmenti spenti, appena visibili
    private static final String[] SEGMENTI = {
            "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"};

    /** Disegna i due quadrati: [data / ore]  [giorno / minuti]. */
    static Bitmap creaImmagine(Context context, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);

        Date ora = new Date();
        Locale loc = Locale.getDefault();
        String ore = new SimpleDateFormat("HH", loc).format(ora);
        String minuti = new SimpleDateFormat("mm", loc).format(ora);
        String data = new SimpleDateFormat("dd/MM/yy", loc).format(ora);
        String giorno = new SimpleDateFormat("EEEE", loc).format(ora).toLowerCase(loc);

        float spazio = w * 0.045f;
        float lato = Math.min((w - spazio) / 2f, h);
        float x0 = (w - (lato * 2 + spazio)) / 2f;
        float y0 = (h - lato) / 2f;

        Paint pq = new Paint(Paint.ANTI_ALIAS_FLAG);
        pq.setColor(COLORE_QUADRATO);

        Typeface fTesto = context.getResources().getFont(R.font.inter_testo);

        for (int i = 0; i < 2; i++) {
            float l = x0 + i * (lato + spazio);
            cv.drawRoundRect(new RectF(l, y0, l + lato, y0 + lato), lato * 0.12f, lato * 0.12f, pq);
            disegnaQuadrato(cv, l, y0, lato, i == 0 ? ore : minuti, i == 0 ? data : null,
                    i == 0 ? null : giorno, fTesto);
        }
        return bmp;
    }

    private static void disegnaQuadrato(Canvas cv, float l, float t0, float S,
                                        String cifre, String data, String giorno, Typeface fTesto) {
        Paint acceso = new Paint(Paint.ANTI_ALIAS_FLAG);
        acceso.setColor(COLORE_NUMERI);
        acceso.setStyle(Paint.Style.FILL);
        Paint spento = new Paint(acceso);
        spento.setColor(COLORE_SPENTO);

        // numeri grandi, centrati nel quadrato
        float hh = S * 0.56f, ww = hh * 0.5f, sp = hh * 0.115f, gap = S * 0.10f;
        float xs = l + (S - (2 * ww + gap)) / 2f;
        float ys = t0 + (S - hh) / 2f;
        float arrot = S * 0.012f;  // vertici leggermente stondati
        acceso.setPathEffect(new android.graphics.CornerPathEffect(arrot));
        spento.setPathEffect(new android.graphics.CornerPathEffect(arrot));
        for (int k = 0; k < 2; k++) {
            float x = xs + k * (ww + gap);
            cifra(cv, '8', x, ys, ww, hh, sp, spento);          // ombra dei segmenti spenti
            cifra(cv, cifre.charAt(k), x, ys, ww, hh, sp, acceso);
        }

        // centro verticale della fascia tra bordo alto e cima dei numeri
        float cy = t0 + ((S - hh) / 2f - sp / 2f) / 2f;

        if (data != null) {
            float dh = S * 0.07f, dw = dh * 0.5f, dt = dh * 0.16f, dgap = dh * 0.28f, sl = dh * 0.35f;
            Paint pd = new Paint(acceso);
            pd.setPathEffect(new android.graphics.CornerPathEffect(arrot * 0.5f));
            float tot = dt;
            for (int k = 0; k < data.length(); k++) {
                char c = data.charAt(k);
                tot += (c == '/' ? sl : c == '1' ? dw * 0.45f : dw) + (k > 0 ? dgap : 0);
            }
            float x = l + S / 2f - tot / 2f + dt / 2f;
            float top = cy - dh / 2f;
            for (int k = 0; k < data.length(); k++) {
                char c = data.charAt(k);
                float wc = c == '/' ? sl : c == '1' ? dw * 0.45f : dw;
                if (c == '/') {
                    float tw = dt * 0.55f;
                    android.graphics.Path p = new android.graphics.Path();
                    p.moveTo(x + wc - tw, top); p.lineTo(x + wc + tw, top);
                    p.lineTo(x + tw, top + dh); p.lineTo(x - tw, top + dh); p.close();
                    Paint ps = new Paint(acceso); ps.setPathEffect(null);
                    cv.drawPath(p, ps);
                } else if (c >= '0' && c <= '9') {
                    cifra(cv, c, x, top, wc, dh, dt, pd);
                }
                x += wc + dgap;
            }
        }

        if (giorno != null) {
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
            pt.setColor(COLORE_NUMERI);
            pt.setTypeface(fTesto);
            pt.setTextAlign(Paint.Align.CENTER);
            pt.setTextSize(S * 0.078f);
            android.graphics.Rect b = new android.graphics.Rect();
            pt.getTextBounds(giorno, 0, giorno.length(), b);
            cv.drawText(giorno, l + S / 2f, cy - b.exactCenterY(), pt);
        }
    }

    /** Disegna una cifra a 7 segmenti (solo i segmenti accesi per quella cifra). */
    private static void cifra(Canvas cv, char c, float x, float y, float w, float h, float t, Paint p) {
        String on = SEGMENTI[c - '0'];
        float g = t * 0.12f, hw = t / 2f;
        float lh = w - 2 * g, lv = h / 2f - 2 * g;
        for (char s : on.toCharArray()) {
            android.graphics.Path path;
            switch (s) {
                case 'a': path = orizz(x + w / 2f, y, lh, hw); break;
                case 'g': path = orizz(x + w / 2f, y + h / 2f, lh, hw); break;
                case 'd': path = orizz(x + w / 2f, y + h, lh, hw); break;
                case 'f': path = vert(x, y + h / 4f, lv, hw); break;
                case 'b': path = vert(x + w, y + h / 4f, lv, hw); break;
                case 'e': path = vert(x, y + 3 * h / 4f, lv, hw); break;
                default:  path = vert(x + w, y + 3 * h / 4f, lv, hw); break; // 'c'
            }
            cv.drawPath(path, p);
        }
    }

    private static android.graphics.Path orizz(float cx, float cy, float L, float hw) {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx - L / 2f, cy);
        p.lineTo(cx - L / 2f + hw, cy - hw);
        p.lineTo(cx + L / 2f - hw, cy - hw);
        p.lineTo(cx + L / 2f, cy);
        p.lineTo(cx + L / 2f - hw, cy + hw);
        p.lineTo(cx - L / 2f + hw, cy + hw);
        p.close();
        return p;
    }

    private static android.graphics.Path vert(float cx, float cy, float L, float hw) {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx, cy - L / 2f);
        p.lineTo(cx + hw, cy - L / 2f + hw);
        p.lineTo(cx + hw, cy + L / 2f - hw);
        p.lineTo(cx, cy + L / 2f);
        p.lineTo(cx - hw, cy + L / 2f - hw);
        p.lineTo(cx - hw, cy - L / 2f + hw);
        p.close();
        return p;
    }
}
