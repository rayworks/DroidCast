package com.rayworks.droidcast;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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
        textView.setText(TextUtils.Companion.format("Source apk Dir:", srcLocation));
    }
}
