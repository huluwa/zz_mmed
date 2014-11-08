package com.sfox.beautyeveryday;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;

public class AboutActivity extends Activity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_about);
        
        TextView view = (TextView) findViewById(R.id.app_version);
        view.setText("v" + Utils.getVerName(this));
        
        findViewById(R.id.about_container).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
