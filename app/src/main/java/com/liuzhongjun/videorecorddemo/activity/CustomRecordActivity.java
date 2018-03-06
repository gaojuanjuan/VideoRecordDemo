package com.liuzhongjun.videorecorddemo.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.liuzhongjun.videorecorddemo.R;
import com.liuzhongjun.videorecorddemo.util.FileSizeUtil;
import com.liuzhongjun.videorecorddemo.util.VideoUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by pc on 2017/3/20.
 *
 * @author liuzhongjun
 */

public class CustomRecordActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "CustomRecordActivity";
    public static final int CONTROL_CODE = 1;
    //UI
    private ImageView mRecordControl;
    private SurfaceView surfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Chronometer mRecordTime;

    //DATA
    private boolean isRecording;// 标记，判断当前是否正在录制
    private long mPauseTime = 0;           //录制暂停时间间隔

    // 存储文件
    private File mVecordFile;
    private Camera mCamera;
    private MediaRecorder mediaRecorder;
    private String currentVideoFilePath;
    private String saveVideoPath = "";
    private int surfaceHolderWidth = 1280;
    private int surfaceHolderHeight = 720;

    private Handler mHandler = new MyHandler(this);
    private List<Camera.Size> mSupportedVideoSizes;
    private String videoFileName = "sign_video.mp4";
    private TextView mTvAgainRecord;
    private ImageView mIvPlay;
    ;

    private static class MyHandler extends Handler {
        private final WeakReference<CustomRecordActivity> mActivity;

        public MyHandler(CustomRecordActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            System.out.println(msg);
            if (mActivity.get() == null) {
                return;
            }
            switch (msg.what) {
                case CONTROL_CODE:
                    //开启按钮
                    mActivity.get().mRecordControl.setEnabled(true);
                    break;
            }
        }
    }


    private MediaRecorder.OnErrorListener OnErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //无title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //全屏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_custom);
        initView();

    }

    private void initView() {
        surfaceView = (SurfaceView) findViewById(R.id.record_surfaceView);
        mRecordControl = (ImageView) findViewById(R.id.record_control);
        mRecordTime = (Chronometer) findViewById(R.id.record_time);
        mTvAgainRecord = ((TextView) findViewById(R.id.tv_again_record));
        mIvPlay = ((ImageView) findViewById(R.id.iv_play));
        mIvPlay.setOnClickListener(this);
        mRecordControl.setOnClickListener(this);
        mTvAgainRecord.setOnClickListener(this);
        //配置SurfaceHodler
        mSurfaceHolder = surfaceView.getHolder();
        // 设置Surface不需要维护自己的缓冲区
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        // 设置分辨率
        mSurfaceHolder.setFixedSize(surfaceHolderWidth, surfaceHolderHeight);
        // 设置该组件不会让屏幕自动关闭
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.addCallback(mCallBack);//回调接口
        mRecordControl.requestFocus();
    }


    private Camera.Size mPreviewSize;
    private SurfaceHolder.Callback mCallBack = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            initCamera();

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            if (mSurfaceHolder.getSurface() == null) {
                return;
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            stopCamera();
        }
    };


    /**
     * 初始化摄像头
     *
     * @throws IOException
     * @author liuzhongjun
     * @date 2016-3-16
     */
    private void initCamera() {
        if (mCamera != null) {
            stopCamera();
        }
        //默认启动后置摄像头
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        //根据width和height去选取Camera最优预览尺寸
        mSupportedVideoSizes = getSupportedVideoSizes(mCamera);
        if (mSupportedVideoSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedVideoSizes,
                    Math.max(surfaceHolderWidth, surfaceHolderHeight), Math.min(surfaceHolderWidth, surfaceHolderHeight));
            Log.d(TAG, "mPreviewSize.width = " + mPreviewSize.width + ",mPreviewSize.height" + mPreviewSize.height);
        }
        if (mCamera == null) {
            Toast.makeText(this, "未能获取到相机！", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            //配置CameraParams
            setCameraParams();
            //启动相机预览
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }


    private void setCameraParams() {
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            if (mPreviewSize != null) {
                params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            }
            //缩短Recording启动时间
            params.setRecordingHint(true);
            //影像稳定能力
            if (params.isVideoStabilizationSupported())
                params.setVideoStabilization(true);
            mCamera.setParameters(params);
        }
    }


    /**
     * 释放摄像头资源
     *
     * @author liuzhongjun
     * @date 2016-2-5
     */
    private void stopCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 开始录制视频
     */
    public void startRecord() {
        initCamera();
        mCamera.unlock();
        setConfigRecord();
        try {
            //开始录制
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        isRecording = true;
        if (mPauseTime != 0) {
            mRecordTime.setBase(SystemClock.elapsedRealtime() - (mPauseTime - mRecordTime.getBase()));
        } else {
            mRecordTime.setBase(SystemClock.elapsedRealtime());
        }
        mRecordTime.start();
    }

    /**
     * 停止录制视频
     */
    public void stopRecord() {
        if (isRecording && mediaRecorder != null) {
            // 设置后不会崩
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setPreviewDisplay(null);
            //停止录制
            mediaRecorder.stop();
            mediaRecorder.reset();
            //释放资源
            mediaRecorder.release();
            mediaRecorder = null;

            //停止计时器
            mRecordTime.stop();
            isRecording = false;
        }
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_again_record://重录按钮的点击事件
                mIvPlay.setVisibility(View.GONE);
                mTvAgainRecord.setVisibility(View.GONE);
                mRecordControl.setVisibility(View.VISIBLE);
                mRecordControl.requestFocus();
                mRecordControl.performClick();
                break;
            case R.id.iv_play://播放按钮的点击事件
                //代表视频暂停录制，后点击中心（即继续录制视频）
                Intent intent = new Intent(CustomRecordActivity.this, PlayVideoActivity.class);
                Bundle bundle = new Bundle();
                if (saveVideoPath.equals("")) {
                    bundle.putString("videoPath", currentVideoFilePath);
                } else {
                    bundle.putString("videoPath", saveVideoPath);
                }
                intent.putExtras(bundle);
                startActivity(intent);
                break;
            case R.id.record_control://录制按钮的点击事件
                if (!isRecording) {//开始录制
                    Toast.makeText(this, "开始录制视频", Toast.LENGTH_SHORT).show();
                    //开始录制视频
                    startRecord();
                    mRecordControl.setImageResource(R.drawable.record_pause_selector);
                    mRecordControl.setEnabled(false);//1s后才能停止
                    mHandler.sendEmptyMessageDelayed(CONTROL_CODE, 1000);
                } else {//停止录制
                    Toast.makeText(this, "停止录制视频", Toast.LENGTH_SHORT).show();
                    Log.d(TAG,"视频文件大小为："+FileSizeUtil.getAutoFileOrFilesSize(currentVideoFilePath));
                    mIvPlay.setVisibility(View.VISIBLE);
                    mIvPlay.requestFocus();
                    mTvAgainRecord.setVisibility(View.VISIBLE);
                    mRecordControl.setVisibility(View.GONE);
                    //停止视频录制
                    mRecordControl.setImageResource(R.drawable.record_selector);
                    stopRecord();
                    mCamera.lock();
                    stopCamera();
                    mRecordTime.stop();
                    mPauseTime = 0;

                }
                break;
        }

    }


    /**
     * 创建视频文件保存路径
     */
    private boolean createRecordDir() {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Toast.makeText(this, "请查看您的SD卡是否存在！", Toast.LENGTH_SHORT).show();
            return false;
        }

        File sampleDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Record");
        if (!sampleDir.exists()) {
            sampleDir.mkdirs();
        }
        String recordName = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
        mVecordFile = new File(sampleDir, recordName);
        currentVideoFilePath = mVecordFile.getAbsolutePath();
        return true;
    }


    public static String getSDPath(Context context) {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();
        } else if (!sdCardExist) {

            Toast.makeText(context, "SD卡不存在", Toast.LENGTH_SHORT).show();

        }
        File eis = new File(sdDir.toString() + "/Video/");
        try {
            if (!eis.exists()) {
                eis.mkdir();
            }
        } catch (Exception e) {

        }
        return sdDir.toString() + "/Video/";
    }


    /**
     * 配置MediaRecorder()
     */
    private void setConfigRecord() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.reset();
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setOnErrorListener(OnErrorListener);


        //设置音频源
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        //设置视频源
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        //设置文件输出格式
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        //设置音频的编码方式
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //设置视频的编码方式
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        //设置音频编码比特率
        mediaRecorder.setAudioEncodingBitRate(44100);
        if (mProfile.videoBitRate > 2 * 1024 * 1024)
            mediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024);//设置音频的编码比特率，可以提高视频的清晰度
        else
            mediaRecorder.setVideoEncodingBitRate(1024 * 1024);
       // 设置录制的视频帧率。必须放在设置编码和格式的后面，否则报错
        mediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        // 设置视频录制的分辨率。必须放在设置编码和格式的后面，否则报错
        mediaRecorder.setVideoSize(mPreviewSize.width, mPreviewSize.height);

        //使用SurfaceView预览
        mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        //设置录像视频保存地址
        currentVideoFilePath = getSDPath(getApplicationContext()) + getVideoName();
        File saveFile = new File(currentVideoFilePath);
        if (saveFile.exists()) {
            saveFile.delete();
            Log.d(TAG, "删除了之前的视频文件");
        }
        Log.d(TAG, "视频保存路径：" + currentVideoFilePath);
        mediaRecorder.setOutputFile(currentVideoFilePath);
    }

    private String getVideoName() {
        return videoFileName;
    }

    /**
     * 获得相机硬件支持的视频分辨率
     * @param camera
     * @return
     */
    public List<Camera.Size> getSupportedVideoSizes(Camera camera) {
        if (camera.getParameters().getSupportedVideoSizes() != null) {
            return camera.getParameters().getSupportedVideoSizes();
        } else {
            // Video sizes may be null, which indicates that all the supported
            // preview sizes are supported for video recording.
            return camera.getParameters().getSupportedPreviewSizes();
        }
    }

    public Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
}
