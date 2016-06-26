package com.gonza.abraham.bounce;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.abraham.android.bounce.About;
import com.abraham.android.bounce.NavigationDrawerFragment;
import com.abraham.android.bounce.R;
import com.abraham.android.bounce.Settings;
import com.google.gson.Gson;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.PlayConfig;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;


public class MusicScreen extends AppCompatActivity implements NavigationDrawerFragment.OnFragmentInteractionListener, PlayerNotificationCallback, ConnectionStateCallback {

    /*Offsets for the arrays that hold the song/playlist info*/
    private static final int NAME = 0;
    private static final int ARTIST = 1;
    private static final int ID = 2;
    private static final int ALBUM = 3;

    /*Tag for the Logs*/
    public final String MATag = "MusicScreen";
    public final String BTag = "Bounce";

    /*Holds the setting items in the NavBar*/
    public String[] menuItemsNavBar = {"About", "Settings"};

    /*Array that holds the playlist items in the NavBar that holds the playlist items*/
    public ArrayList<String> itemsInPlaylistNavBar;
    /*Holds the Playlist names and Ids*/
    public ArrayList<ArrayList<String>> playlistDataArray;
    /*Holds the TrackNames, Artists and the Ids*/
    public ArrayList<ArrayList<String>> trackDataArray;
    public ArrayList<SongListResults> songListResultsArrayList;

    /*Adapter for the custom listView song objects*/
    public MyCustomBaseAdapter customBaseAdapter;
    /*Adapter for the playlist items in NavBar*/
    public ArrayAdapter playlistNavDrawAdapter;

    /*NavBar fragment*/
    public NavigationDrawerFragment drawerFragment;
    
    /*Items for the pop-up window (including inflator)*/
    PopupWindow popupWindow;
    LayoutInflater popupLayoutInflator;
    LinearLayout mainMusicLinearLayout;

    /*Misc*/
    public MusicPlayerData mpData;
    boolean refreshing = false;
    boolean shuffle_active = false;
    private Toolbar toolbar;
    private int SETTING_REQUEST = 100;
    String message = "";
    ServerSocket serverSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_screen);

        /*Setup the toolbar and navigation drawer*/
        toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        drawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_navigation_drawer);
        drawerFragment.setUp(R.id.fragment_navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        /*The initialization for the lists and the different elements of the layout*/
        initializeLayout();
        
        /*Sets up the Spotify player (Authenticates and Sets up a login)(Makes sure that this portion is not done twice)*/
        if (savedInstanceState == null) {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(MusicPlayerData.CLIENT_ID, AuthenticationResponse.Type.TOKEN, MusicPlayerData.REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming"});
            AuthenticationRequest request = builder.build();
            AuthenticationClient.openLoginActivity(MusicScreen.this, MusicPlayerData.REQUEST_CODE, request);
        }
    }

    private void initializeLayout() {
        /*Creates the music data object that contains the mpData.state of the player*/
        mpData = new MusicPlayerData();

         /*Setups up the slide-in menu*/
        ListView drawerListView = (ListView) findViewById(R.id.drawer_listview);
        drawerListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuItemsNavBar));
        drawerListView.setVisibility(View.VISIBLE);
        drawerListView.setOnItemClickListener(new DrawerItemClickListener());

        /*Setup the playlist dropdown menu*/
        itemsInPlaylistNavBar = new ArrayList<>();
        ListView navPlaylistListView = (ListView) findViewById(R.id.nav_drawer_playlist_listview);
        playlistNavDrawAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, itemsInPlaylistNavBar);
        navPlaylistListView.setAdapter(playlistNavDrawAdapter);
        navPlaylistListView.setVisibility(View.VISIBLE);
        navPlaylistListView.setOnItemClickListener(new DrawerItemClickListener());

        /*Sets up the global names in the playlists that are passed back and forth between threads*/
        playlistDataArray = new ArrayList<>();
        playlistDataArray.add(new ArrayList<String>()); //For Playlist Names
        playlistDataArray.add(new ArrayList<String>()); //For Playlist IDs

        /*Sets up the track data that is being passed between the list and networking thread*/
        trackDataArray = new ArrayList<>();
        trackDataArray.add(new ArrayList<String>()); //For Track Names
        trackDataArray.add(new ArrayList<String>()); //For Track Artists
        trackDataArray.add(new ArrayList<String>()); //For Track Albums
        trackDataArray.add(new ArrayList<String>()); //For Track Ids
        songListResultsArrayList = new ArrayList<>();

        /*Initialize the mpData.currentTrackData array*/
        mpData.currentTrackData = new String[4];
        mpData.currentTrackData[ID] = "spotify:track:2TpxZ7JUBn3uw46aR7qd6V";

        /*Set up the listView for the playlistSongs*/
        ListView playlistListView;
        playlistListView = (ListView) findViewById(R.id.playlist_listview);
        customBaseAdapter = new MyCustomBaseAdapter(this, songListResultsArrayList);
        playlistListView.setAdapter(customBaseAdapter);
        playlistListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Log.d(BTag + "/" + MATag, "Song selected from playlist in Listview");

                /*Get the Id of the song in the list as well as the index*/
                mpData.currentTrackData[ID] = trackDataArray.get(ID).get(pos);
                for(int i = 0; i < mpData.choiceList.length; i++){
                    if(pos == mpData.choiceList[i]){
                        mpData.currentIndexInChoiceList = i;
                    }
                }

                /*Change the image of the play/stop button*/
                ImageButton playButton = (ImageButton) findViewById(R.id.play_stop_button);
                playButton.setImageResource(R.drawable.ic_pause_circle_filled_black_24dp);

                /*Play song and fill out the song data*/
                mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);

                /*Get the Id of the song in the list as well as the index*/
                /*Get the UserId using the mpData.statemachine in the other thread*/
                new NetworkingThread().execute(State.GET_SONG_DATA);
            }
        });

        /*Setup the seekbar for music time control*/
        SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
        songSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                final TextView pointInSong = (TextView) findViewById(R.id.song_point);
                /*Sets the value of the slider to what the person is moving it to*/
                if (mpData.isSliderMoving) {
                    final int point = (progress * mpData.currentLengthOfSong) / 100;
                    MusicScreen.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pointInSong.setText(String.format("%02d:%02d", (point / 60000), ((point % 60000) / 1000)));
                        }
                    });
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mpData.isSliderMoving = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                final TextView pointInSong = (TextView) findViewById(R.id.song_point);
                mpData.isSliderMoving = false;
                /*mpData.states the location of the song from the trackbar*/
                mpData.iPointBarValue = (seekBar.getProgress() * mpData.currentLengthOfSong) / 100;
                MusicScreen.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pointInSong.setText(String.format("%02d:%02d", (mpData.iPointBarValue / 60000), ((mpData.iPointBarValue % 60000) / 1000)));
                    }
                });
                if (mpData.isPlayingMusic) {
                    /*Plays the music with that start*/
                    PlayConfig myConfig = PlayConfig.createFor("spotify:track:" + mpData.currentTrackData[ID]);
                    myConfig.withInitialPosition(mpData.iPointBarValue);
                    mpData.mPlayer.play(myConfig);
                }
            }
        });

        /*The buttons on the Music Bar*/
        final ImageButton playStopButton = (ImageButton) findViewById(R.id.play_stop_button);
        final ImageButton forwardButton = (ImageButton) findViewById(R.id.forward_button);
        final ImageButton backButton = (ImageButton) findViewById(R.id.back_button);
        final ImageButton shuffleButton = (ImageButton) findViewById(R.id.shuffle_button);

        /*Set up onClick listener for the buttons*/
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == playStopButton) {
                    Log.d(BTag + "/" + MATag, "Play Stop Button Clicked");
                    /*Specifies the button for the rest of the program*/
                    final ImageButton playStopButton = (ImageButton) findViewById(R.id.play_stop_button);

                    if (!mpData.isPlayingMusic && mpData.isSongStarted) {
                        /*Play song from a certain position*/
                        PlayConfig myConfig = PlayConfig.createFor("spotify:track:" + mpData.currentTrackData[ID]);
                        myConfig.withInitialPosition(mpData.iPointBarValue);
                        mpData.mPlayer.play(myConfig);
                        MusicScreen.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                playStopButton.setImageResource(R.drawable.ic_pause_circle_filled_black_24dp);
                            }
                        });
                        mpData.isPlayingMusic = true;
                    } else if (mpData.isPlayingMusic) {
                         /*Pause song at the certain point*/
                        mpData.mPlayer.pause();
                        MusicScreen.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                playStopButton.setImageResource(R.drawable.ic_play_circle_filled_black_24dp);
                            }
                        });
                        mpData.isPlayingMusic = false;
                    }
                } else if (v == forwardButton) {
                    Log.d(BTag + "/" + MATag, "Forward Button Clicked");

                     /*Moves to the next song in the list and plays it*/
                    if (mpData.isSongStarted) {
                        if (mpData.currentIndexInChoiceList < trackDataArray.get(NAME).size() - 1) {
                            mpData.currentIndexInChoiceList = ++mpData.currentIndexInChoiceList;
                            mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                            new NetworkingThread().execute(State.GET_SONG_DATA);
                        } else {
                        /*This starts the first song if you are at the last song in the list*/
                            mpData.currentIndexInChoiceList = 0;
                            mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                            new NetworkingThread().execute(State.GET_SONG_DATA);
                        }
                        MusicScreen.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                playStopButton.setImageResource(R.drawable.ic_pause_circle_filled_black_24dp);
                            }
                        });
                    }
                } else if (v == backButton) {
                    Log.d(BTag + "/" + MATag, "Back Button clicked");

                    if (mpData.isSongStarted) {
                        final SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
                        final TextView songName = (TextView) findViewById(R.id.song_name);
                        final TextView artistName = (TextView) findViewById(R.id.artist_name);
                        final TextView songLengthTotal = (TextView) findViewById(R.id.song_length);

                     /*Enable click once to go to the beginning and a double-click to go back a song*/
                        if (mpData.iPointBarValue >= 20) {
                            mpData.iPointBarValue = 0;
                            mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                            songSeekBar.setProgress(0);
                            mpData.isPlayingMusic = true;
                            mpData.isSongStarted = true;
                        } else if (mpData.currentIndexInChoiceList > 0) {
                         /*Moves the index back and goes to the previous song in the list*/
                            mpData.currentIndexInChoiceList = --mpData.currentIndexInChoiceList;
                            mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.choiceList[mpData.currentIndexInChoiceList]);
                            mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                            new NetworkingThread().execute(State.GET_SONG_DATA);
                        }
                        MusicScreen.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                playStopButton.setImageResource(R.drawable.ic_pause_circle_filled_black_24dp);
                            }
                        });
                    }
                }
                else if (v == shuffleButton) {
                    Log.d(BTag + "/" + MATag, "Shuffle Button clicked");

                    if (mpData.isSongStarted) {
                        if (!shuffle_active) {
                            shuffleButton.setImageResource(R.drawable.ic_shuffle_blue_24dp);
                            Log.d(BTag + "/" + MATag, "Shuffle Enabled");

                            /*Turn on shuffle by randomizing choice list*/
                            Random rnd = ThreadLocalRandom.current();
                            for (int i = mpData.choiceList.length - 1; i > 0; i--) {
                                int index = rnd.nextInt(i + 1);
                                int a = mpData.choiceList[index];
                                mpData.choiceList[index] = mpData.choiceList[i];
                                mpData.choiceList[i] = a;
                            }

                            shuffle_active = true;
                        } else {
                            Log.d(BTag + "/" + MATag, "Shuffle Disabled");
                            shuffleButton.setImageResource(R.drawable.ic_shuffle_black_24dp);
                            /*Re-Index the current list*/
                            mpData.currentIndexInChoiceList = mpData.choiceList[mpData.currentIndexInChoiceList];

                            /*Turn off shuffle by ordering the choice list*/
                            for (int i = 0; i < mpData.choiceList.length; i++) {
                                mpData.choiceList[i] = i;
                            }

                            shuffle_active = false;
                        }
                    }
                }
            }
        };

        playStopButton.setOnClickListener(listener);
        forwardButton.setOnClickListener(listener);
        backButton.setOnClickListener(listener);
        shuffleButton.setOnClickListener(listener);

        mainMusicLinearLayout = (LinearLayout)findViewById(R.id.linear_layout_music);

        TextView song_name_txtview = (TextView) findViewById(R.id.song_name);
        TextView artist_name_txtview = (TextView) findViewById(R.id.artist_name);
        song_name_txtview.setSelected(true);
        artist_name_txtview.setSelected(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_music_screen, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfmpData.statement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        /*Check to make sure that the activity that finished was the Spotify activity*/
        if (requestCode == MusicPlayerData.REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                /*Setup a new configuration for Spotify*/
                Config playerConfig = new Config(this, response.getAccessToken(), MusicPlayerData.CLIENT_ID);
                mpData.accessToken = response.getAccessToken();

                /*Get the UserId using the mpData.statemachine in the other thread*/
                new NetworkingThread().execute(State.GET_USER_ID);

                mpData.mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mpData.mPlayer.addConnectionStateCallback(MusicScreen.this);
                        mpData.mPlayer.addPlayerNotificationCallback(MusicScreen.this);
                        /*Have another thread make periodic callbacks inorder to refresh seek bar*/
                        SongThread mySongThread = new SongThread();
                        mySongThread.Init();
                        mySongThread.start();
                        Log.d(BTag + "/" + MATag, "Refreshing Playlists");
                        refreshing = true;
                        /*Use the mpData.statemachine in the NetworkThread to retrieve the playlists*/
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                        new NetworkingThread().execute(State.GET_PLAYLISTS);

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(BTag + "/" + MATag, "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
        else if(requestCode == SETTING_REQUEST){
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);

            /*Find out if the Activity wants to connect to another phone or not*/
            if(pref.getBoolean("DoConnection",false)){
                Log.d(BTag + "/" + MATag, "Connection Settings requested");
                /*Create a connection*/
                if(pref.getBoolean("Host", false)){
                    Log.d(BTag + "/" + MATag, "Create host");
                    /*Hosting AKA is a server*/
                    Thread socketServerThread = new Thread(new SocketServerThread());
                    socketServerThread.start();
                }
                else{
                    Log.d(BTag + "/" + MATag, "Create client");
                    /*Not hosting AKA is a client*/
                    MyClientTask myTask = new MyClientTask(pref.getString("IpAddress", "Default"), Integer.parseInt(pref.getString("PortNumber", "Default")));
                    myTask.execute();

                    Log.d(BTag + "/" + MATag, "Pop-Up initiated");
                    popupLayoutInflator = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                    ViewGroup container = (ViewGroup)popupLayoutInflator.inflate(R.layout.popup_connection_window,null);
                    popupWindow = new PopupWindow(container,400,400,true);
                    popupWindow.showAtLocation(mainMusicLinearLayout, Gravity.CENTER, 0, 0);

                }
            }
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(drawerFragment.isVisible()){
            drawerFragment.closeDrawer();
        }
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
        Log.d(BTag + "/" + MATag, "Spotify Player destroyed");
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    /*Enum for mpData.statemachine in NetworkingThread*/
    private enum State {
        IDLE,
        GET_SONG_DATA,
        GET_PLAYLISTS,
        GET_USER_ID,
        GET_PLAYLIST_SONGS
    }

    private class MusicPlayerData {
        /*Specific codes in order to access the Spotify API*/
        private static final String CLIENT_ID = "ccf6c320eb754ab0bd13358005a32e4f";
        private static final String REDIRECT_URI = "bounceredirect://callback";
        private static final int REQUEST_CODE = 1337;
        /*Holds current data about the song*/
        public int currentIndexInChoiceList;
        public int[] choiceList;
        public int currentLengthOfSong;
        /*Holds the Name, Artist and Id of the current song*/
        public String[] currentTrackData;
        public String currentUserId;
        public String currentPlaylistId;
        /*Data values for the slider*/
        public int iPointBarValue = 0;
        public boolean isSliderMoving = false;
        /*Flags for the music player*/
        public boolean isPlayingMusic = false;
        public boolean isSongStarted = false;
        /*State of the statemachine that is running*/
        public State state = State.IDLE;
        /*Access token for Spotify*/
        public String accessToken;

        /*Spotify Player*/
        private Player mPlayer;
    }

    private class NetworkingThread extends AsyncTask<MusicScreen.State, Integer, MusicScreen.State> {
        public String httpGet(String urlStr, String accessToken) throws IOException {
            try {
            /*Send a Get response to the specific endpoint*/
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                if (accessToken != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                }
                if (conn.getResponseCode() != 200) {
                    throw new IOException(conn.getResponseMessage());
                }

            /* Buffer the result into a string */
                BufferedReader rd = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    sb.append(line);
                }

            /*Close the connection and the reader*/
                rd.close();
                conn.disconnect();

            /*Put the string into the global variable*/
                return sb.toString();

            } catch (Exception myException) {
            /*Error occurred when sending the Get request*/
                return null;
            }
        }

        @Override
        protected State doInBackground(State... params) {
            /*mpData.statemachine that sends out the Get requests and proceses the incoming data*/
            if (params[0] == MusicScreen.State.GET_PLAYLIST_SONGS) {
                getPlaylistSongs();
            } else if (params[0] == MusicScreen.State.GET_PLAYLISTS) {
                getPlaylist();
            } else if (params[0] == MusicScreen.State.GET_SONG_DATA) {
                getSongData();
            } else if (params[0] == MusicScreen.State.GET_USER_ID) {
                getUserID();
            }

            return params[0];
        }

        @Override
        protected void onPostExecute(State state) {
            if (state == State.GET_SONG_DATA) {
                Log.d(BTag + "/" + MATag, "Song Data Requested");

                final SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);
                final TextView songName = (TextView) findViewById(R.id.song_name);
                final TextView artistName = (TextView) findViewById(R.id.artist_name);
                final TextView songLengthTotal = (TextView) findViewById(R.id.song_length);

                songName.setText(mpData.currentTrackData[NAME]);
                artistName.setText(mpData.currentTrackData[ARTIST]);
                songLengthTotal.setText(String.format("%02d:%02d", (mpData.currentLengthOfSong / 60000), ((mpData.currentLengthOfSong % 60000) / 1000)));
                songSeekBar.setProgress(0);
                mpData.isPlayingMusic = true;
                mpData.isSongStarted = true;
            } else if (state == State.GET_PLAYLIST_SONGS) {
                Log.d(BTag + "/" + MATag, "Plylst Songs requested");
                customBaseAdapter.notifyDataSetChanged();

                /*Generate the choice array*/
                mpData.choiceList = new int[trackDataArray.get(NAME).size()];
                if(!shuffle_active){
                    /*Make the choice list in order*/
                    for (int i = 0; i < mpData.choiceList.length; i++){
                        mpData.choiceList[i] = i;
                    }
                }
                else{
                    /*Make the choice list shuffled*/
                    Random rnd = ThreadLocalRandom.current();
                    for (int i = mpData.choiceList.length - 1; i > 0; i--)
                    {
                        int index = rnd.nextInt(i + 1);
                        int a = mpData.choiceList[index];
                        mpData.choiceList[index] = mpData.choiceList[i];
                        mpData.choiceList[i] = a;
                    }
                }
            } else if (state == State.GET_PLAYLISTS) {
                /*Add the pertinient data to the ListView*/
                for (int i = 0; i < playlistDataArray.get(NAME).size(); i++) {
                    itemsInPlaylistNavBar.add(playlistDataArray.get(NAME).get(i));
                }
                playlistNavDrawAdapter.notifyDataSetChanged();
            }
        }

        private void getSongData() {
            try {
                Log.d(BTag + "/" + MATag, "Retrieving Song Data");

                /*Query the Spotify API*/
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/tracks/" + mpData.currentTrackData[ID], null);
                Gson gObj = new Gson();
                Track track = gObj.fromJson(jsonString, Track.class);

                /*Assign specified data to the pertinent arrays*/
                mpData.currentTrackData[NAME] = track.name;
                mpData.currentTrackData[ARTIST] = track.artists[0].name;
                mpData.currentTrackData[ALBUM] = track.album.name;
                mpData.currentLengthOfSong = track.duration_ms;
                Log.d(BTag + "/" + MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(BTag + "/" + MATag, "Failure to retrieve song data");
            }
        }

        private void getPlaylist() {
            try {
                Log.d(BTag + "/" + MATag, "Retrieving Playlist Data");

                /*Query the Spotify API*/
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/users/" + mpData.currentUserId + "/playlists", mpData.accessToken);
                Gson gObj = new Gson();
                UsersPlaylistsPaging myPlaylists = gObj.fromJson(jsonString, UsersPlaylistsPaging.class);

                /*Assign the specified data to the pertinent arrays*/
                playlistDataArray.get(NAME).clear();
                playlistDataArray.get(1).clear();
                for (int i = 0; i < myPlaylists.items.length; i++) {
                    playlistDataArray.get(NAME).add(myPlaylists.items[i].name);
                    playlistDataArray.get(1).add(myPlaylists.items[i].id);
                }
                Log.d(BTag + "/" + MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(BTag + "/" + MATag, "Failure to retrieve playlist data");
            }
        }

        private void getUserID() {
            try {
                Log.d(BTag + "/" + MATag, "Retrieving User ID");

                /*Query the Spotify API*/
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/me", mpData.accessToken);
                Gson gObj = new Gson();
                User user = gObj.fromJson(jsonString, User.class);

                /*Assign the User Id*/
                mpData.currentUserId = user.id;
                Log.d(BTag + "/" + MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(BTag + "/" + MATag, "Failure to retrieve User ID");
            }
        }

        private void getPlaylistSongs() {
            try {
                Log.d(BTag + "/" + MATag, "Retrieving playlist songs");

                /*Query the Spotify API*/
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/users/" + mpData.currentUserId + "/playlists/" + mpData.currentPlaylistId + "/tracks", mpData.accessToken);
                Gson gObj = new Gson();
                PlaylistTracksPaging myPlaylistTracks = gObj.fromJson(jsonString, PlaylistTracksPaging.class);

                /*Assign the data to the pertinent arrays*/
                trackDataArray.get(NAME).clear();
                trackDataArray.get(ARTIST).clear();
                trackDataArray.get(ALBUM).clear();
                trackDataArray.get(ID).clear();
                songListResultsArrayList.clear();
                int totalAmtOfSongs = myPlaylistTracks.total;

                do {
                    for (int i = 0; i < myPlaylistTracks.items.length; i++) {
                        trackDataArray.get(NAME).add(myPlaylistTracks.items[i].track.name);
                        trackDataArray.get(ARTIST).add(myPlaylistTracks.items[i].track.artists[0].name + " - " + myPlaylistTracks.items[i].track.album.name);
                        trackDataArray.get(ID).add(myPlaylistTracks.items[i].track.id);
                        songListResultsArrayList.add(new SongListResults(myPlaylistTracks.items[i].track.name, myPlaylistTracks.items[i].track.artists[0].name + " - " + myPlaylistTracks.items[i].track.album.name));
                    }
                    totalAmtOfSongs -= 100;
                    if (totalAmtOfSongs > 0) {
                        jsonString = httpGet(myPlaylistTracks.next, mpData.accessToken);
                        gObj = new Gson();
                        myPlaylistTracks = gObj.fromJson(jsonString, PlaylistTracksPaging.class);
                    }
                } while (totalAmtOfSongs > 0);
                Log.d(BTag + "/" + MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(BTag + "/" + MATag, "Failure retrieving playlist songs");
            }
        }

        public class User {
            private String birthdate;
            private String country;
            private String display_name;
            private String email;
            //private ExternalURL eternal_urls;
            private Followers followers;
            private String href;
            private String id;
            private ImageType[] images;
            private String product;
            private String type;
            private String uri;
        }

        public class Followers {
            private String href;
            private int total;
        }

        public class ImageType {
            private int height;
            private String url;
            private int width;
        }

        public class Track {
            private SimplifiedAlbum album;
            private SimplifiedArtist[] artists;
            private String[] available_markets;
            private int disc_number;
            private int duration_ms;
            private boolean explicit;
            //private ExternalID external_ids;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private boolean is_playable;
            //private LinkedTrack linked_from;
            private String name;
            private int popularity;
            private String preview_url;
            private int track_number;
            private String type;
            private String uri;
        }

        public class SimplifiedAlbum {
            private String album_type;
            private String[] available_markets;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private ImageType[] images;
            private String name;
            private String type;
            private String uri;
        }

        public class SimplifiedArtist {
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private String name;
            private String type;
            private String uri;
        }

        public class PlaylistTrack {
            //private TimeStamp added_at;
            private User added_by;
            private boolean is_local;
            private Track track;
        }

        public class PlaylistTracksPaging {
            private String href;
            private PlaylistTrack[] items;
            private int limit;
            private String next;
            private int offset;
            private String previous;
            private int total;
        }

        public class SimplifiedPlaylist {
            private boolean collaborative;
            //private ExternalURL external_urls;
            private String href;
            private String id;
            private ImageType[] images;
            private String name;
            private User owner;
            //private bool/null public;
            private String snapshot_id;
            //private Track tracks; -> has string href; and string total;
            private String type;
            private String uri;
        }

        public class UsersPlaylistsPaging {
            private String href;
            private SimplifiedPlaylist[] items;
            private int limit;
            private String next;
            private int offset;
            private String previous;
            private int total;
        }
    }

    private class SongThread extends Thread {

        PlayerStateCallback myCallback;

        public void Init() {
            myCallback = new PlayerStateCallback() {
                @Override
                public void onPlayerState(PlayerState playerState) {
                    final PlayerState myState = playerState;
                    final TextView pointInSong = (TextView) findViewById(R.id.song_point);
                    final SeekBar songSeekBar = (SeekBar) findViewById(R.id.player_seek_bar);

                    if (playerState.playing) {
                        if (mpData.isPlayingMusic) {
                            if (!mpData.isSliderMoving) {
                                Log.d(BTag + "/" + MATag, "Seekbar Updated with new position");
                                /*Move the seekbar to the position that the song is at*/
                                MusicScreen.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        pointInSong.setText(String.format("%02d:%02d", (myState.positionInMs / 60000), ((myState.positionInMs % 60000) / 1000)));
                                        songSeekBar.setProgress(((myState.positionInMs * 100) / mpData.currentLengthOfSong));
                                    }
                                });
                            }
                            mpData.iPointBarValue = playerState.positionInMs;
                        }
                    }
                }
            };
        }

        public void run() {
            while (true) {
                try {
                    sleep(500);
                } catch (Exception myException) {
                    /*An error occured when trying to sleep this thread*/
                }
                if (mpData.isPlayingMusic) {
                    mpData.mPlayer.getPlayerState(myCallback);
                }
            }
        }
    }

    public class SongListResults {
        private String songName = "";
        private String artistAlbumName = "";

        public SongListResults(String songName, String artistName) {
            this.songName = songName;
            this.artistAlbumName = artistName;
        }

        public String getArtistName() {
            return artistAlbumName;
        }

        public String getSongName() {
            return songName;
        }
    }

    public class MyCustomBaseAdapter extends BaseAdapter {
        private ArrayList<SongListResults> songListResultsArrayList;

        private LayoutInflater mInflater;

        public MyCustomBaseAdapter(Context context, ArrayList<SongListResults> results) {
            songListResultsArrayList = results;
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return songListResultsArrayList.size();
        }

        public Object getItem(int position) {
            return songListResultsArrayList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.custom_rowview_xml, null);
                holder = new ViewHolder();
                holder.txtSongName = (TextView) convertView.findViewById(R.id.song_name_element);
                holder.txtArtistName = (TextView) convertView.findViewById(R.id.artist_name_element);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.txtSongName.setText(songListResultsArrayList.get(position).getSongName());
            holder.txtArtistName.setText(songListResultsArrayList.get(position).getArtistName());

            holder.txtArtistName.setSelected(true);
            holder.txtSongName.setSelected(true);

            return convertView;
        }

        class ViewHolder {
            TextView txtSongName;
            TextView txtArtistName;
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            Log.d(BTag + "/" + MATag, "NavDrawer Item Clicked");
            ListView drawerListView = (ListView) findViewById(R.id.drawer_listview);
            ListView navPlaylistListView = (ListView) findViewById(R.id.nav_drawer_playlist_listview);

            if(parent == drawerListView) {
            /*Determine where to go when an item in the nav drawer is clicked*/
                String recievedString = (String) drawerListView.getItemAtPosition(position);
                Intent sendIntent;

                if (recievedString == "About") {
                    sendIntent = new Intent(getApplicationContext(), About.class);
                    startActivity(sendIntent);
                } else if (recievedString == "Settings") {
                    sendIntent = new Intent(getApplicationContext(), Settings.class);
                    startActivityForResult(sendIntent, SETTING_REQUEST);
                }
            }
            else if (parent == navPlaylistListView){
                if (!refreshing) {
                    final AdapterView<?> parentVar = parent;
                    final int posVar = position;
                    Log.d(BTag + "/" + MATag, "New Playlist Selected in ListView");
                    mpData.currentPlaylistId = playlistDataArray.get(ARTIST).get(position);

                    TextView playlistName = (TextView) findViewById(R.id.app_bar_textView);
                    playlistName.setText((String)navPlaylistListView.getItemAtPosition(position));
                    new NetworkingThread().execute(State.GET_PLAYLIST_SONGS);
                }
                refreshing = false;
            }
        }
    }

    public class MyClientTask extends AsyncTask<Void, Void, Void> {
        String dstAddress;
        int dstPort;
        String response = "";
        MyClientTask(String addr, int port) {
            dstAddress = addr;
            dstPort = port;
        }
        @Override
        protected Void doInBackground(Void... arg0) {
            Socket socket = null;
            try {
                socket = new Socket(dstAddress, dstPort);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];
                int bytesRead;
                InputStream inputStream = socket.getInputStream();
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (IOException e) {
                e.printStackTrace();
                response = "IOException: " + e.toString();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            /*Recieved from the server*/
            popupWindow.dismiss();
            super.onPostExecute(result);
        }
    }

    private class SocketServerThread extends Thread {
        //static final int SocketServerPORT = 8080;
        int count = 0;
        int mLocalPort;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MusicScreen.this);
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(Integer.parseInt(pref.getString("PortNumber", "Default")));

                while (true) {
                    Socket socket = serverSocket.accept();
                    Toast.makeText(MusicScreen.this,"Client Connected", Toast.LENGTH_SHORT).show();
                    count++;
                    Calendar cal = Calendar.getInstance();
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    message = "This is from the serverThread" + sdf.format(cal.getTime());
                    MusicScreen.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            /*Message recieved from the client*/
                            //Use message
                        }
                    });
                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket, count);
                    socketServerReplyThread.run();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SocketServerReplyThread extends Thread {
        int cnt;
        private Socket hostThreadSocket;

        SocketServerReplyThread(Socket socket, int c) {
            hostThreadSocket = socket;
            cnt = c;
        }

        @Override
        public void run() {
            OutputStream outputStream;
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String msgReply = "Server->Client" + sdf.format(cal.getTime());
            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(msgReply);
                printStream.close();
                message = "Server->Server" + sdf.format(cal.getTime());
                MusicScreen.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //serverMessage.setText(message);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                message = "Something wrong! " + e.toString();
            }
            MusicScreen.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //serverMessage.setText(message);
                }
            });
        }
    }

}