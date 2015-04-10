package com.chris.medusacam;

import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by Chris on 1/6/15.
 */
public class ThreadClass {

    public static final int PROCESS_PHOTO = 1;
    public static final int PROCESS_VIDEO = 2;

    private static final int VIDEO_INIT_GAP = 3000;
    private static final int VIDEO_SAMPLING_PERIOD = 100;

    // bit map recording the running status of each thread (1 for photo, 2 for video)
    private boolean [] threadRunning;

    public ThreadClass() {
        threadRunning = new boolean[]{false, false, false};         // init the running record
    }

    public void startThread (int tType, final String filePath, final String dataPath, final String dataName) {
        Thread thread;
        switch (tType) {

            case PROCESS_PHOTO:                         /* photo */
                thread = new Thread() {
                    @Override
                    public void run() { processPhoto (filePath, dataPath, dataName); }
                };
                threadRunning[tType] = true;
                thread.start();
                break;

            case PROCESS_VIDEO:                          /* video */
                thread = new Thread() {
                    @Override
                    public void run() {
                        File dataDir = new File(dataPath);  // create the log file
                        if (!dataDir.exists())
                            dataDir.mkdirs();
                        File dataFile = new File(   dataPath + "/" +
                                                    MainActivity.MP4_FILE_PREFIX +
                                                    filePath.split(MainActivity.MP4_FILE_PREFIX)[1].split(MainActivity.MP4_FILE_SUFFIX)[0] +
                                                    ".txt");
                        FileWriter fileWriter;
                        try {
                            dataFile.createNewFile();
                            fileWriter = new FileWriter(dataFile,true);
                        } catch (IOException e) {
                            MainActivity.transMsg(MainActivity.POST_MSG,"Cannot create/write file! \n ----------------------");
                            e.printStackTrace();
                            return;
                        }

                        try {                               // sleep for a while before user takes video
                            sleep(VIDEO_INIT_GAP);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        BufferedWriter bufferWriter = new BufferedWriter(fileWriter);
                        while (threadRunning[PROCESS_VIDEO]) {                  // loop for recording metadata
                            try {
                                bufferWriter.write(
                                            "v" + " " +                         // "name" of the metadata
                                            MainActivity.getMetadata(MainActivity.ACTION_TAKE_VIDEO) + "\n");   // metadata
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                sleep(VIDEO_SAMPLING_PERIOD);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        try {                                       // close writing metadata file
                            bufferWriter.close();
                            MainActivity.transMsg(MainActivity.POST_MSG, "video metadata stored! \n ---------------------");
                        } catch (IOException e) {
                            MainActivity.transMsg(MainActivity.POST_MSG, "cannot close metadata file!");
                            e.printStackTrace();
                        }
                    }
                };
                threadRunning[tType] = true;
                thread.start();
                break;

            default:
                MainActivity.transMsg(MainActivity.POST_MSG, "wrong thread type!!!");
                break;
        }
    }

    public void stopThread (int tType) {
        switch (tType) {
            case PROCESS_VIDEO:
            case PROCESS_PHOTO:
                threadRunning[tType] = false;
                break;
            default:
                MainActivity.transMsg(MainActivity.POST_MSG, "wrong thread type!!!");
                break;
        }
    }

    /** Func for processing photo */
    private void processPhoto (final String filePath, final String dataPath, final String dataName) {
        MainActivity.transMsg(MainActivity.POST_MSG, "Processing Image.......please wait........");
        String img_name = filePath.split("/")[filePath.split("/").length - 1];
        String tmpMsg =
                        img_name + " " +             // image name
                        MainActivity.getMetadata(MainActivity.ACTION_TAKE_PHOTO) + "\n";        // metadata
        MainActivity.transMsg(MainActivity.POST_MSG, tmpMsg);
        MainActivity.fileClass.appendFile(dataPath, dataName, tmpMsg);
        /*
                                                        // ------------- this part is mod for samsung: move the file
        Boolean moveSuc = MainActivity.fileClass.moveFile(
                dataPath + '/' + img_name,
                Environment.getExternalStorageDirectory() + MainActivity.CAMERA_DIR + MainActivity.ALBUM_NAME
        );

        if (moveSuc)
            MainActivity.transMsg(MainActivity.POST_MSG, "moving file success!");
        else
            MainActivity.transMsg(MainActivity.POST_MSG, "moving file failed!");
        */
    }
}