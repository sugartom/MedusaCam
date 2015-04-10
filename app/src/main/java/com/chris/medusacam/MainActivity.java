package com.chris.medusacam;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

/**
 * Author: Xiaochen (Chris) Liu
 * Date: 0105
 * Version: v0.1
 * Function: Template for Android App building
 */
public class MainActivity extends Activity {

    /** Final Values */

    public static final String JPEG_FILE_PREFIX = "IMG_";
    public static final String JPEG_FILE_SUFFIX = ".jpg";
    public static final String MP4_FILE_PREFIX = "VID_";
    public static final String MP4_FILE_SUFFIX = ".mp4";
    public static final String CAMERA_DIR = "/DCIM/";
    public static final String DATA_DIR = "MedusaCam_data";
    public static final String ALBUM_NAME = "Camera";
    public static final String PHOTO_RECORD_NAME = "MetadataPhoto.txt";

    public static final int ACTION_TAKE_PHOTO = 1;      // taking pictures
    public static final int ACTION_TAKE_VIDEO = 2;      // taking videos

    public static final int POST_MSG = 1;               // send message to handler
    public static final int POST_TOAST = 2;
    public static final int ADD_GALLERY = 3;

    /** Functional Classes */
    public static Handler msgHandler;           // handler for displaying debug text
    public static SensorClass sensorClass;      // class of sensor controller [modify if need to process sensor data]
    public static ThreadClass threadClass;      // thread controlling class [modify if need to define the func of thread]
    public static FileClass fileClass;          // file controlling class
    public static ExifClass exifClass;          // editing EXIF data
    public static FaceClass faceClass;          // detect faces in a photo
    public static BlurClass blurClass;          // detect whether the photo is blurry or not

    private Camera mCamera;

    public static String filePath;               // path that the photo is stored in
    public static String dataPath;
    private LocationManager locMan;
    private MyLocationListener locLis;
    public static double lng,lat,acc;             // gps values
    public static double thetaH;                  // camera parameters

    /** Variables */
    public static String sceneTag;
    public static int cars;                       // number or cars

    /** UI widgets */
    private TextView debug_txt;        // textView that shows debug information
    private Button photo_btn;          // button
    private Button video_btn;          // button
    private ScrollView scroll;
    private RadioGroup radioGroup;
    private RadioButton radioButton1, radioButton2;
    private TextView sb_name;
    private SeekBar seekBar;

    /** Utility Functions */

    // public func for posting info in debug text
    public static void transMsg(int msgType, String msgString){
        Message msg = msgHandler.obtainMessage(msgType);
        Bundle bundle = new Bundle();
        bundle.putString("transMsg", msgString);
        msg.setData(bundle);
        msgHandler.sendMessage(msg);
    }

    // scroll the scroll to the bottom
    private void scrollToBottom(final ScrollView scroll, final View inner){
        if(scroll == null || inner == null){ return; }
        // if inner view's height larger than scroll
        int offset = inner.getMeasuredHeight() - scroll.getMeasuredHeight();
        if(offset < 0){ offset = 0; }
        scroll.scrollTo(0, offset);
    }

    // init msg Handler
    private void initMsgHandler(){
        msgHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String comingMsg = msg.getData().getString("transMsg");
                switch (msg.what) {
                    case POST_MSG:              // display debug msg on screen
                        debug_txt.append("\n" + comingMsg);
                        break;
                    case POST_TOAST:            // display toast on screen
                        Toast.makeText(MainActivity.this, comingMsg, Toast.LENGTH_SHORT).show();
                        break;
                    case ADD_GALLERY:
                        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
                        File f = new File(comingMsg);
                        Uri contentUri = Uri.fromFile(f);
                        mediaScanIntent.setData(contentUri);
                        sendBroadcast(mediaScanIntent);
                        break;
                    default:
                        break;
                }
                scrollToBottom(scroll, debug_txt);      // scroll to bottom
            }
        };
    }

    /*
     GPS related
     */
    public class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            if (loc != null) {
                Log.i("main GPS","loc changed");
                lng = loc.getLongitude();
                lat = loc.getLatitude();
                acc = loc.getAccuracy();
            }
        }
        @Override
        public void onProviderDisabled(String provider) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    private void turnGPSOn(){
        String provider = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

        if(!provider.contains("gps")){      //if gps is disabled
            Log.i("main","try to turn on GPS");
            Intent intent=new Intent("android.location.GPS_ENABLED_CHANGE");
            intent.putExtra("enabled", true);
            sendBroadcast(intent);
            transMsg(POST_TOAST,"GPS is on");
        }
    }

    /*
     SYSTEM FUNCTIONS
     */

    @Override
    protected void onResume() {
        super.onResume();
        if(sensorClass != null) {
            sensorClass.openSensor(SensorClass.LIGHT_ON, SensorManager.SENSOR_DELAY_NORMAL);
            sensorClass.openSensor(SensorClass.ACC_ON, SensorManager.SENSOR_DELAY_NORMAL);
            sensorClass.openSensor(SensorClass.MAG_ON, SensorManager.SENSOR_DELAY_NORMAL);
        }

        String currentProvider = LocationManager.NETWORK_PROVIDER;
        Log.d("on resume", "CurrentProvider: " + currentProvider);
        Location lastKnownLocation = locMan.getLastKnownLocation(currentProvider);
        if (lastKnownLocation != null) {
            Log.d("on resume", "LastKnownLocation: "
                    + lastKnownLocation.getLongitude() + " "
                    + lastKnownLocation.getLatitude() + " "
                    + lastKnownLocation.getAccuracy());
        } else {
            Log.d("on resume", "Last Location unknown!");
        }
        locMan.requestLocationUpdates(currentProvider, 0, 0, locLis);
        scrollToBottom(scroll, debug_txt);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(sensorClass != null) {
            sensorClass.sensorManager.unregisterListener(sensorClass.mySensorListener);
        }
        locMan.removeUpdates(locLis);
        Log.d("on pause", "LocationListener removed.");
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        Log.i("main.onCreate","on create");

        // initiate UI
        debug_txt = (TextView)findViewById(R.id.debug_txt);
        photo_btn = (Button)findViewById(R.id.photo_btn);
        video_btn = (Button)findViewById(R.id.video_btn);
        scroll = (ScrollView)findViewById(R.id.scroll);
        radioGroup = (RadioGroup)findViewById(R.id.radiogroup);
        radioButton1 = (RadioButton)findViewById(R.id.radiobutton1);
        radioButton2 = (RadioButton)findViewById(R.id.radiobutton2);
        sb_name = (TextView)findViewById(R.id.sb_name);
        seekBar = (SeekBar)findViewById(R.id.seekBar);

        initMsgHandler();           // create msg handler

        // Specify the UI widgets
        sceneTag = "Indoor";
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if(checkedId == radioButton1.getId()) { sceneTag = "Indoor"; }
                else if (checkedId == radioButton2.getId()) { sceneTag = "Outdoor"; }
            }
        });

        /*
          init functional classes
         */
        sensorClass = new SensorClass();
        threadClass = new ThreadClass();
        fileClass = new FileClass();
        // exifClass = new ExifClass();
        faceClass = new FaceClass();
        blurClass = new BlurClass();

        /*
          set click listener
         */
        photo_btn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                try {
                    takePhoto(ALBUM_NAME);      // input album name
                }catch (IOException e) {
                    e.printStackTrace();
                    transMsg(POST_MSG,"cannot take photo...");
                }
            }
        });

        video_btn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                try {
                    takeVideo(ALBUM_NAME);      // input album name
                }catch (IOException e) {
                    e.printStackTrace();
                    transMsg(POST_MSG,"cannot take photo...");
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cars = seekBar.getProgress();
                if(cars < 6) {
                    sb_name.setText("\n" + cars + " cars in the photo");
                }
                else {
                    sb_name.setText("\n" + "6 or more cars in the photo");
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
        });

        // sensors
        sensorClass.sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        sensorClass.openSensor(SensorClass.LIGHT_ON, SensorManager.SENSOR_DELAY_NORMAL);
        sensorClass.openSensor(SensorClass.ACC_ON, SensorManager.SENSOR_DELAY_NORMAL);
        sensorClass.openSensor(SensorClass.MAG_ON, SensorManager.SENSOR_DELAY_NORMAL);

        // gps
        turnGPSOn();
        locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location loc = locMan.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        locLis = new MyLocationListener();
        locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locLis);

        // camera
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        thetaH = Math.toRadians(parameters.getHorizontalViewAngle());
        mCamera.release();

        // init the data storage path
        dataPath = Environment.getExternalStorageDirectory() + CAMERA_DIR + DATA_DIR;
    }

    /*
     Action when hit "take photo" button
     */
    public void takePhoto(String albumName) throws IOException {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File f;
        try {
            f = fileClass.setUpMediaFile(albumName, ACTION_TAKE_PHOTO);
            if (f == null)
                return;
            filePath = f.getAbsolutePath();
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        } catch (IOException e) {
            transMsg(POST_MSG,"cannot take photo...io problem");
            e.printStackTrace();
            return;
        }
        startActivityForResult(takePictureIntent, ACTION_TAKE_PHOTO);
    }

    /*
     Action when hit "take video" button
     */
    public void takeVideo(String albumName) throws IOException {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        File f;
        try {
            f = fileClass.setUpMediaFile(albumName, ACTION_TAKE_VIDEO);
            if (f == null)
                return;
            filePath = f.getAbsolutePath();
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        } catch (IOException e) {
            transMsg(POST_MSG,"cannot take video...io problem");
            e.printStackTrace();
            return;
        }
        threadClass.startThread(ThreadClass.PROCESS_VIDEO, filePath, dataPath, "");
        startActivityForResult(takeVideoIntent, ACTION_TAKE_VIDEO);
    }

    /*
     Action when finish taking photo/video
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0){                  // activity returned error
            transMsg(POST_MSG, "Activity returned error!");
        }
        else if (requestCode == ACTION_TAKE_PHOTO || requestCode == ACTION_TAKE_VIDEO) {            // valid return
            File mediaFile = new File(filePath);
            if(!mediaFile.exists()){
                transMsg(POST_MSG,"No File!\n--------------------");
                return;
            }
            if(mediaFile.length() < 100){
                transMsg(POST_MSG,"Photo or video not taken!\n--------------------");
                if (mediaFile.delete())
                    transMsg(POST_MSG, "temp file deleted");
                else
                    transMsg(POST_MSG, "cannot delete temp file!");
                return;
            }
            transMsg(POST_MSG, "File size: " + mediaFile.length()/1000 + "KB");
            transMsg(ADD_GALLERY, filePath);
            if (requestCode == ACTION_TAKE_PHOTO)
                threadClass.startThread(ThreadClass.PROCESS_PHOTO, filePath, dataPath, PHOTO_RECORD_NAME);    // process photo
            else
                threadClass.stopThread(ThreadClass.PROCESS_VIDEO);      // stop recording the sensor data
        }
    }

    /*
      get metadata
     */
    public static String getMetadata (int type) {
        int faceNum = 0;
        int blurry = 0;
        int video_len = 0;

        switch (type) {
            case ACTION_TAKE_PHOTO:
                faceNum = 0; // faceClass.faceDetect(MainActivity.filePath);
                blurry = 0; // sblurClass.blurDetect(MainActivity.filePath);
                break;
            case ACTION_TAKE_VIDEO:
                long video_len_l = (System.currentTimeMillis() - 0) / 1000;
                if (video_len_l < Integer.MIN_VALUE || video_len_l > Integer.MAX_VALUE)
                    transMsg(POST_MSG, "error long to int in getMetadata!");
                else {
                    video_len = (int) video_len_l;
                    transMsg(POST_MSG, "video length: " + video_len);
                }
                break;
            default:
                break;
        }



        /*
            Medusa:             "C2DM_ID", UID, "CEDD(img)", lat, lng, Create_time, File_size
            Cam:                "file_name", cars, faces, blur, video_length,
                                "sceneTag, AngleOfView, Light, ACC(3), Mag(3), Bearing(3), Loc&Acc(3)"

            Video_meta_file:    "timestamp, AngleOfView, Light, ACC(3), Mag(3), Bearing(3), Loc&Accuracy(3)"
         */
        return
                /* int */
                        cars +                                                          // num of cars
                        " " + faceNum +                                                 // num of faces
                        " " + blurry +                                                  // blurry or not (0 or 1)
                        // " " + video_len +                                               // video length (sec)
                /* char */
                        " " + sceneTag +                                                // scene tag
                /* float/double */
                        " " + String.valueOf(MainActivity.thetaH) +                     // angle of view
                        " " + sensorClass.getSensorData(SensorClass.LIGHT_ON) +         // light sensor
                        " " + sensorClass.getSensorData(SensorClass.ACC_ON) +           // acc value (3) - x,y,z
                        " " + sensorClass.getSensorData(SensorClass.MAG_ON) +           // mag value (3) - x,y,z
                        " " + sensorClass.getBearingInfo() +                            // bearing (3) - azimuth, pitch, roll
                        " " + lng + " " + lat + " " + acc;                              // GPS (3) - lng, lat, accuracy
    }
}