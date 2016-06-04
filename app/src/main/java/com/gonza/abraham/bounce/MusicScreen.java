package com.gonza.abraham.bounce;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;


public class MusicScreen extends AppCompatActivity implements NavigationDrawerFragment.OnFragmentInteractionListener, PlayerNotificationCallback, ConnectionStateCallback {

    private static final int NAME = 0;
    private static final int ARTIST = 1;
    private static final int ID = 2;
    public final String MATag = "MusicPlayerActivity";

    /*Holds the items in the navigation bar*/
    public String[] menuItems = {"About", "Settings"};

    /*Is the spinner that holds the playlist items*/
    public ArrayList<String> itemsInSpinner;

    /*Holds the Playlist names and Ids*/
    public ArrayList<ArrayList<String>> playlistDataArray;
    /*Holds the TrackNames, Artists and the Ids*/
    public ArrayList<ArrayList<String>> trackDataArray;
    public ArrayList<SongListResults> songListResultsArrayList;

    //public ArrayAdapter playlistListViewAdapter;
    public MyCustomBaseAdapter customBaseAdapter;
    public ArrayAdapter playlistSpinnerAdapter;

    public MusicPlayerData mpData;
    boolean refreshing = false;
    private Toolbar toolbar;
    private Spinner spinner_nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_screen);

        /*Setup the toolbar and navigation drawer*/
        toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        NavigationDrawerFragment drawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_navigation_drawer);
        drawerFragment.setUp(R.id.fragment_navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), toolbar);
        NavigationDrawerFragment drawerFragment2 = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_navigation_drawer2);
        drawerFragment.setUp(R.id.fragment_navigation_drawer2, (DrawerLayout) findViewById(R.id.drawer_layout), toolbar);
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
        drawerListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuItems));
        drawerListView.setVisibility(View.VISIBLE);
        drawerListView.setOnItemClickListener(new DrawerItemClickListener());

        /*Setup the playlist dropdown menu*/
        itemsInSpinner = new ArrayList<>();
        playlistSpinnerAdapter = new ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, itemsInSpinner);

        spinner_nav = (Spinner) findViewById(R.id.spinner_nav);
        spinner_nav.setVisibility(View.VISIBLE);
        spinner_nav.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!refreshing) {
                    final AdapterView<?> parentVar = parent;
                    final int posVar = position;
                    Log.d("Bounce", "Playlist Changed in Spinner");
                    mpData.currentPlaylistId = playlistDataArray.get(ARTIST).get(position);

                    new NetworkingThread().execute(State.GET_PLAYLIST_SONGS);
                }
                refreshing = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinner_nav.setAdapter(playlistSpinnerAdapter);


        /*Sets up the global names in the playlists that are passed back and forth between threads*/
        playlistDataArray = new ArrayList<>();
        playlistDataArray.add(new ArrayList<String>()); //For Playlist Names
        playlistDataArray.add(new ArrayList<String>()); //For Playlist IDs

        /*Sets up the track data that is being passed between the list and networking thread*/
        trackDataArray = new ArrayList<>();
        trackDataArray.add(new ArrayList<String>()); //For Track Names
        trackDataArray.add(new ArrayList<String>()); //For Track Artists
        trackDataArray.add(new ArrayList<String>()); //For Track Ids
        songListResultsArrayList = new ArrayList<>();

        /*Initialize the mpData.currentTrackData array*/
        mpData.currentTrackData = new String[3];
        mpData.currentTrackData[ID] = "spotify:track:2TpxZ7JUBn3uw46aR7qd6V";

        //playlistListViewAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, trackDataArray.get(NAME));
        ListView playlistListView;
        playlistListView = (ListView) findViewById(R.id.playlist_listview);
        customBaseAdapter = new MyCustomBaseAdapter(this, songListResultsArrayList);
        playlistListView.setAdapter(customBaseAdapter);
        playlistListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Log.d("Bounce", "Song selected in Listview");

        /*Get the Id of the song in the list as well as the index*/
                mpData.currentTrackData[ID] = trackDataArray.get(ID).get(pos);
                mpData.currentIndexInList = pos;
                ImageButton playButton = (ImageButton) findViewById(R.id.play_stop_button);
                playButton.setImageResource(R.drawable.pause);

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


        final ImageButton playStopButton = (ImageButton) findViewById(R.id.play_stop_button);
        final ImageButton forwardButton = (ImageButton) findViewById(R.id.forward_button);
        final ImageButton backButton = (ImageButton) findViewById(R.id.back_button);

        /*Set up onClick listener for the buttons*/
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == playStopButton) {
                    Log.d("Bounce", "Play Stop Button Clicked");
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
                                playStopButton.setImageResource(R.drawable.pause);
                            }
                        });
                        mpData.isPlayingMusic = true;
                    } else if (mpData.isPlayingMusic) {
                         /*Pause song at the certain point*/
                        mpData.mPlayer.pause();
                        MusicScreen.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                playStopButton.setImageResource(R.drawable.play);
                            }
                        });
                        mpData.isPlayingMusic = false;
                    }
                } else if (v == forwardButton) {
                    Log.d("Bounce", "Forward Button Clicked");

                     /*Moves to the next song in the list and plays it*/
                    if (mpData.currentIndexInList < trackDataArray.get(NAME).size() - 1) {
                        mpData.currentIndexInList = ++mpData.currentIndexInList;
                        mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.currentIndexInList);
                        mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.currentIndexInList);
                        mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                        new NetworkingThread().execute(State.GET_SONG_DATA);
                    } else {
                        /*This starts the first song if you are at the last song in the list*/
                        mpData.currentIndexInList = 0;
                        mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.currentIndexInList);
                        mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.currentIndexInList);
                        mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                        new NetworkingThread().execute(State.GET_SONG_DATA);
                    }
                    MusicScreen.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playStopButton.setImageResource(R.drawable.pause);
                        }
                    });
                } else if (v == backButton) {
                    Log.d("Bounce", "Back Button clicked");
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
                    } else if (mpData.currentIndexInList > 0) {
                         /*Moves the index back and goes to the previous song in the list*/
                        mpData.currentIndexInList = --mpData.currentIndexInList;
                        mpData.currentTrackData[ID] = trackDataArray.get(ID).get(mpData.currentIndexInList);
                        mpData.currentTrackData[NAME] = trackDataArray.get(NAME).get(mpData.currentIndexInList);
                        mpData.mPlayer.play("spotify:track:" + mpData.currentTrackData[ID]);
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                        new NetworkingThread().execute(State.GET_SONG_DATA);
                    }
                    MusicScreen.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playStopButton.setImageResource(R.drawable.pause);
                        }
                    });
                }
            }
        };

        playStopButton.setOnClickListener(listener);
        forwardButton.setOnClickListener(listener);
        backButton.setOnClickListener(listener);
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
                        Log.d("Bounce", "Refreshing Playlists");
                        refreshing = true;
                        /*Use the mpData.statemachine in the NetworkThread to retrieve the playlists*/
                        /*Get the UserId using the mpData.statemachine in the other thread*/
                        new NetworkingThread().execute(State.GET_PLAYLISTS);

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
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
        super.onDestroy();
        Log.d("MainActivity", "Spotify Player destroyed");
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
        public int currentIndexInList;
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
                //playlistListViewAdapter.notifyDataSetChanged();
                customBaseAdapter.notifyDataSetChanged();
            } else if (state == State.GET_PLAYLISTS) {
                /*Add the pertinient data to the ListView*/
                for (int i = 0; i < playlistDataArray.get(NAME).size(); i++) {
                    itemsInSpinner.add(playlistDataArray.get(NAME).get(i));
                }
                playlistSpinnerAdapter.notifyDataSetChanged();
            }
        }

        private void getSongData() {
            try {
                Log.d(MATag, "Retrieving Song Data");
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/tracks/" + mpData.currentTrackData[ID], null);
                Gson gObj = new Gson();
                Track track = gObj.fromJson(jsonString, Track.class);
                mpData.currentTrackData[NAME] = track.name;
                mpData.currentTrackData[ARTIST] = track.artists[0].name;
                mpData.currentLengthOfSong = track.duration_ms;
                Log.d(MATag, "Successful retrieval");
            } catch (Exception myException) {
                                /*An error occured when running the mpData.statemachine*/
                Log.d(MATag, "Failure to retrieve song data");
            }
        }

        private void getPlaylist() {
            try {
                Log.d(MATag, "Retrieving Playlist Data");
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/users/" + mpData.currentUserId + "/playlists", mpData.accessToken);
                Gson gObj = new Gson();
                UsersPlaylistsPaging myPlaylists = gObj.fromJson(jsonString, UsersPlaylistsPaging.class);
                playlistDataArray.get(NAME).clear();
                playlistDataArray.get(1).clear();

                for (int i = 0; i < myPlaylists.items.length; i++) {
                    playlistDataArray.get(NAME).add(myPlaylists.items[i].name);
                    playlistDataArray.get(1).add(myPlaylists.items[i].id);
                }
                Log.d(MATag, "Successful retrieval");
            } catch (Exception myException) {
                                /*An error occured when running the mpData.statemachine*/
                Log.d(MATag, "Failure to retrieve playlist data");
            }
        }

        private void getUserID() {
            try {
                Log.d(MATag, "Retrieving User ID");
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/me", mpData.accessToken);
                Gson gObj = new Gson();
                User user = gObj.fromJson(jsonString, User.class);
                mpData.currentUserId = user.id;
                Log.d(MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(MATag, "Failure to retrieve User ID");
            }
        }

        private void getPlaylistSongs() {
            try {
                Log.d(MATag, "Retrieving playlist songs");
                String jsonString;
                jsonString = httpGet("https://api.spotify.com/v1/users/" + mpData.currentUserId + "/playlists/" + mpData.currentPlaylistId + "/tracks", mpData.accessToken);
                Gson gObj = new Gson();
                PlaylistTracksPaging myPlaylistTracks = gObj.fromJson(jsonString, PlaylistTracksPaging.class);
                trackDataArray.get(NAME).clear();
                trackDataArray.get(ARTIST).clear();
                trackDataArray.get(ID).clear();
                songListResultsArrayList.clear();
                int totalAmtOfSongs = myPlaylistTracks.total;

                do {
                    for (int i = 0; i < myPlaylistTracks.items.length; i++) {
                        trackDataArray.get(NAME).add(myPlaylistTracks.items[i].track.name);
                        trackDataArray.get(ARTIST).add(myPlaylistTracks.items[i].track.artists[0].name);
                        trackDataArray.get(ID).add(myPlaylistTracks.items[i].track.id);
                        songListResultsArrayList.add(new SongListResults(myPlaylistTracks.items[i].track.name, myPlaylistTracks.items[i].track.artists[0].name));
                    }
                    totalAmtOfSongs -= 100;
                    if (totalAmtOfSongs > 0) {
                        jsonString = httpGet(myPlaylistTracks.next, mpData.accessToken);
                        gObj = new Gson();
                        myPlaylistTracks = gObj.fromJson(jsonString, PlaylistTracksPaging.class);
                    }
                } while (totalAmtOfSongs > 0);
                Log.d(MATag, "Successful retrieval");
            } catch (Exception myException) {
                /*An error occured when running the mpData.statemachine*/
                Log.d(MATag, "Failure retrieving playlist songs");
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
                                Log.d(MATag, "Seekbar Updated with new position");
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
        private String artistName = "";

        public SongListResults(String songName, String artistName) {
            this.songName = songName;
            this.artistName = artistName;
        }

        public String getArtistName() {
            return artistName;
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
            Log.d(MATag, "NavDrawer Clicked");
            ListView drawerListView = (ListView) findViewById(R.id.drawer_listview);
            String recievedString = (String) drawerListView.getItemAtPosition(position);
            Intent sendIntent;

            /*Determine where to go when an item in the nav drawer is clicked*/
            if (recievedString == "About") {
                sendIntent = new Intent(getApplicationContext(), About.class);
                startActivity(sendIntent);
            } else if (recievedString == "Settings") {
                sendIntent = new Intent(getApplicationContext(), Settings.class);
                startActivity(sendIntent);
            }
        }
    }
}