package com.abraham.android.bounce;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gonza.abraham.bounce.MusicScreen;


public class LoginScreen extends ActionBarActivity {

    public final static String USERNAME_MESSAGE = "com.abraham.android.bounce.Username";
    public final static String PASSWORD_MESSAGE = "com.abraham.android.bounce.Password";
    private static int AMOUNT_OF_USER_PASS_COMBOS = 2;
    private static int AMOUNT_OF_PASSWORDS_PER_USER = 1;
    public Animation fadeOut;
    public int fadeOutDuration = 3000;
    public TextView loginFailed;
    String[][] databaseStrings = new String[][]{{"abiegonzalez96@gmail.com", "Abie3169"},
            {"svgonzalez89@yahoo.com", "svgo7014"}};
    private String USERNAME_DEFAULT = "DEFAULT_USER";
    private String PASSWORD_DEFAULT = "DEFAULT_PASS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_screen);
        findViewById(R.id.loginFailedTextView).setVisibility(View.INVISIBLE);
        fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(fadeOutDuration);
        loginFailed = (TextView) findViewById(R.id.loginFailedTextView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_login_screen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void Login_Button_Click(View view) {
        findViewById(R.id.progress_bar).setVisibility(View.GONE);
        loginFailed.setVisibility(View.INVISIBLE);

        ProgressBar loginBar = (ProgressBar) findViewById(R.id.progress_bar);
        loginBar.setVisibility(View.VISIBLE);
        if (Send_Check_Database()) {
            loginBar.setVisibility(View.INVISIBLE);
            Switch_to_Main();
        } else {
            loginBar.setVisibility(View.INVISIBLE);
            /*Login Failed*/
            loginFailed.setVisibility(View.VISIBLE);
            loginFailed.startAnimation(fadeOut);
            loginFailed.setVisibility(View.INVISIBLE);
        }
    }

    public void Switch_to_Main() {
        /*Switched this to the NowPlayingScreen*/
        Intent login_intent = new Intent(this, MusicScreen.class);
        startActivity(login_intent);
        finish();
    }

    private boolean Send_Check_Database() {
        EditText username = (EditText) findViewById(R.id.username_edit_text);
        EditText password = (EditText) findViewById(R.id.password_edit_text);
        USERNAME_DEFAULT = username.getText().toString();
        PASSWORD_DEFAULT = password.getText().toString();

        boolean isMatch = false;

        for (int i = 0; i < AMOUNT_OF_USER_PASS_COMBOS; ++i) {
            for (int j = 0; j < AMOUNT_OF_PASSWORDS_PER_USER; ++j) {
                if (USERNAME_DEFAULT.equals(databaseStrings[i][j])) {
                    if (PASSWORD_DEFAULT.equals(databaseStrings[i][j + 1])) {
                        isMatch = true;
                    }
                }
            }
        }

        return isMatch;
    }
}
