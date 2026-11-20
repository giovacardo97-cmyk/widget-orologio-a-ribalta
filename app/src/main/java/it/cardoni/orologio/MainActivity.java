package it.cardoni.orologio;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText(R.string.info);
        tv.setTextColor(Color.parseColor("#DDDDDD"));
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextSize(17);
        tv.setGravity(Gravity.CENTER);
        int p = (int) (32 * getResources().getDisplayMetrics().density);
        tv.setPadding(p, p, p, p);
        setContentView(tv);
        // se il widget è già sulla Home, lo ridisegna subito
        ClockWidget.aggiornaTutti(this);
    }
}
