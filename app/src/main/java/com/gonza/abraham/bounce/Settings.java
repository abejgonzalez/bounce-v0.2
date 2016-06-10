package com.abraham.android.bounce;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.InetAddress;
import java.net.SocketException;

public class Settings extends AppCompatActivity {

    SharedPreferences mPref;
    SharedPreferences.Editor mEditor;
    Switch mHostSwitch;
    Button mConnSetButton;
    EditText mPortNumberTextView;
    EditText mIpAddressTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mPref.edit();
        mEditor.clear();
        mEditor.putBoolean("DoConnection", false);

        mIpAddressTextView = (EditText) findViewById(R.id.ip_address_edittext);
        mPortNumberTextView = (EditText) findViewById(R.id.port_number_edittext);
        mConnSetButton = (Button) findViewById(R.id.connect_set_button);
        mConnSetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditor.putBoolean("DoConnection", true);
                if (mHostSwitch.isChecked()) {
                    mEditor.clear();
                    mEditor.putBoolean("Host",true);
                    mEditor.putString("PortNumber", mPortNumberTextView.getText().toString());
                    while (!mEditor.commit()) {
                        Log.d("SettingsActivity", "Committing to preferences");
                    }
                } else {
                    mEditor.clear();
                    mEditor.putBoolean("Host",false);
                    mEditor.putString("PortNumber", mPortNumberTextView.getText().toString());
                    mEditor.putString("IpAddress", mIpAddressTextView.getText().toString());
                    while (!mEditor.commit()) {
                        Log.d("SettingsActivity", "Committing to preferences");
                    }
                }

                Toast.makeText(Settings.this, "Connection will occur in main screen", Toast.LENGTH_LONG).show();
            }
        });
        mHostSwitch = (Switch) findViewById(R.id.host_switch);
        mHostSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Settings.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mConnSetButton.setText("Set");
                            mIpAddressTextView.setFocusable(false);
                            mIpAddressTextView.setText(getIpAddress());
                        }
                    });
                } else {
                    Settings.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mConnSetButton.setText("Connect");
                            mIpAddressTextView.setFocusable(true);
                        }
                    });

                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_about, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }

        return super.onOptionsItemSelected(item);
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        ip = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            ip = "Something Wrong! " + e.toString();
        }
        return ip;
    }
}