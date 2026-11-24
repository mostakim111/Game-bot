package com.mostakim.gamebot;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("GameBot step 2 OK");
        t.setTextSize(24);
        t.setPadding(40, 200, 40, 40);
        setContentView(t);
    }
}
