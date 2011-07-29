package com.mobvcasting.mjpegffmpeg;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MJPEGFFMPEGTest extends Activity implements OnClickListener, 
									SurfaceHolder.Callback, Camera.PreviewCallback { 
	
	public static final String LOGTAG = "MJPEG_FFMPEG";
	
	private SurfaceHolder holder;
	private CamcorderProfile camcorderProfile;
	private Camera camera;	
	
	byte[] previewCallbackBuffer;

	boolean recording = false;
	boolean previewRunning = false;	
	
	File jpegFile;			
	int fileCount = 0;
	
	FileOutputStream fos;
	BufferedOutputStream bos;
	Button recordButton;
	
	Camera.Parameters p;
	
	NumberFormat fileCountFormatter = new DecimalFormat("00000");
	String formattedFileCount;
	
	ProcessVideo processVideo;
		
	String[] libraryAssets = {"ffmpeg",
			"libavcodec.so", "libavcodec.so.52", "libavcodec.so.52.99.1",
			"libavcore.so", "libavcore.so.0", "libavcore.so.0.16.0",
			"libavdevice.so", "libavdevice.so.52", "libavdevice.so.52.2.2",
			"libavfilter.so", "libavfilter.so.1", "libavfilter.so.1.69.0",
			"libavformat.so", "libavformat.so.52", "libavformat.so.52.88.0",
			"libavutil.so", "libavutil.so.50", "libavutil.so.50.34.0",
			"libswscale.so", "libswscale.so.0", "libswscale.so.0.12.0"
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        for (int i = 0; i < libraryAssets.length; i++) {
			try {
				InputStream ffmpegInputStream = this.getAssets().open(libraryAssets[i]);
		        FileMover fm = new FileMover(ffmpegInputStream,"/data/data/com.mobvcasting.mjpegffmpeg/" + libraryAssets[i]);
		        fm.moveIt();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        Process process = null;
        
        try {
        	String[] args = {"/system/bin/chmod", "755", "/data/data/com.mobvcasting.mjpegffmpeg/ffmpeg"};
        	process = new ProcessBuilder(args).start();        	
        	try {
				process.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	process.destroy();
        	 			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		File savePath = new File(Environment.getExternalStorageDirectory().getPath() + "/com.mobvcasting.mjpegffmpeg/");
		savePath.mkdirs();
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.main);
		
		recordButton = (Button) this.findViewById(R.id.RecordButton);
		recordButton.setOnClickListener(this);
		
		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);

		SurfaceView cameraView = (SurfaceView) findViewById(R.id.CameraView);
		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		cameraView.setClickable(true);
		cameraView.setOnClickListener(this);
    }

	public void onClick(View v) {
		if (recording) 
		{
			recording = false;			
			Log.v(LOGTAG, "Recording Stopped");
			
			// Convert to video
			processVideo = new ProcessVideo();
			processVideo.execute();
		} 
		else 
		{
			recording = true;
			Log.v(LOGTAG, "Recording Started");
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");
		
		camera = Camera.open();
		
		/*
		try {
			camera.setPreviewDisplay(holder);
			camera.startPreview();
			previewRunning = true;
		}
		catch (IOException e) {
			Log.e(LOGTAG,e.getMessage());
			e.printStackTrace();
		}	
		*/
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");

		if (!recording) {
			if (previewRunning){
				camera.stopPreview();
			}

			try {
				p = camera.getParameters();

				p.setPreviewSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
			    p.setPreviewFrameRate(camcorderProfile.videoFrameRate);
				camera.setParameters(p);
				
				camera.setPreviewDisplay(holder);
				
				/*
				Log.v(LOGTAG,"Setting up preview callback buffer");
				previewCallbackBuffer = new byte[(camcorderProfile.videoFrameWidth * camcorderProfile.videoFrameHeight * 
													ImageFormat.getBitsPerPixel(p.getPreviewFormat()) / 8)];
				Log.v(LOGTAG,"setPreviewCallbackWithBuffer");
				camera.addCallbackBuffer(previewCallbackBuffer);				
				camera.setPreviewCallbackWithBuffer(this);
				*/
				
				camera.setPreviewCallback(this);
				
				Log.v(LOGTAG,"startPreview");
				camera.startPreview();
				previewRunning = true;
			}
			catch (IOException e) {
				Log.e(LOGTAG,e.getMessage());
				e.printStackTrace();
			}	
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (recording) {
			recording = false;

			try {
				bos.flush();
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		previewRunning = false;
		camera.release();
		finish();
	}

	public void onPreviewFrame(byte[] b, Camera c) {
		//Log.v(LOGTAG,"onPreviewFrame");
		if (recording) {

			// Assuming ImageFormat.NV21
			if (p.getPreviewFormat() == ImageFormat.NV21) {
				Log.v(LOGTAG,"Started Writing Frame");
				
				try {
					formattedFileCount = fileCountFormatter.format(fileCount);  
					jpegFile = new File(Environment.getExternalStorageDirectory().getPath() + "/com.mobvcasting.mjpegffmpeg/frame_" + formattedFileCount + ".jpg");
					fileCount++;
					
					fos = new FileOutputStream(jpegFile);
					bos = new BufferedOutputStream(fos);
	
					YuvImage im = new YuvImage(b, ImageFormat.NV21, p.getPreviewSize().width, p.getPreviewSize().height, null);
					Rect r = new Rect(0,0,p.getPreviewSize().width,p.getPreviewSize().height);
					im.compressToJpeg(r, 9, bos);							

					bos.flush();
					bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				Log.v(LOGTAG,"Finished Writing Frame");
			} else {
				Log.v(LOGTAG,"NOT THE RIGHT FORMAT");
			}
		}
	}
	
    @Override
    public void onConfigurationChanged(Configuration conf) 
    {
        super.onConfigurationChanged(conf);
    }	
	    
	private class ProcessVideo extends AsyncTask<Void, Integer, Void> {
		@Override
		protected Void doInBackground(Void... params) {

	        Process ffmpegProcess = null;
	        
	        try {
	        	
	        	//ffmpeg -r 10 -b 1800 -i %03d.jpg test1800.mp4
	        	// 00000
	        	// /data/data/com.mobvcasting.ffmpegcommandlinetest/ffmpeg -r p.getPreviewFrameRate() -b 1000 -i frame_%05d.jpg video.mov
	        	
				//String[] args2 = {"/data/data/com.mobvcasting.ffmpegcommandlinetest/ffmpeg", "-y", "-i", "/data/data/com.mobvcasting.ffmpegcommandlinetest/", "-vcodec", "copy", "-acodec", "copy", "-f", "flv", "rtmp://192.168.43.176/live/thestream"};
				String[] ffmpegCommand = {"/data/data/com.mobvcasting.mjpegffmpeg/ffmpeg", "-r", ""+p.getPreviewFrameRate(), "-b", "1000000", "-vcodec", "mjpeg", "-i", Environment.getExternalStorageDirectory().getPath() + "/com.mobvcasting.mjpegffmpeg/frame_%05d.jpg", Environment.getExternalStorageDirectory().getPath() + "/com.mobvcasting.mjpegffmpeg/video.mov"};
				
				ffmpegProcess = new ProcessBuilder(ffmpegCommand).redirectErrorStream(true).start();         	
				
				OutputStream ffmpegOutStream = ffmpegProcess.getOutputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()));

				String line;
				
				Log.v(LOGTAG,"***Starting FFMPEG***");
				while ((line = reader.readLine()) != null)
				{
					Log.v(LOGTAG,"***"+line+"***");
				}
				Log.v(LOGTAG,"***Ending FFMPEG***");
	
	    
	        } catch (IOException e) {
	        	e.printStackTrace();
	        }
	        
	        if (ffmpegProcess != null) {
	        	ffmpegProcess.destroy();        
	        }
	        
	        return null;
		}
		
	     protected void onPostExecute(Void... result) {
	    	 Toast toast = Toast.makeText(MJPEGFFMPEGTest.this, "Done Processing Video", Toast.LENGTH_LONG);
	    	 toast.show();
	     }
	}
 }