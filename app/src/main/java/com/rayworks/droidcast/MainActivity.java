package com.rayworks.droidcast;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /***
         * https://developer.android.google.cn/about/versions/oreo/android-8.0-changes.html
         * #security-all
         */
        ApplicationInfo info = getApplicationInfo();
        String srcLocation = info.sourceDir;

        TextView textView = findViewById(R.id.text);
        textView.setText(String.format(Locale.ENGLISH, "Source apk Dir: %s", srcLocation));

    }
}
