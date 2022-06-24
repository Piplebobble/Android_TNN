package com.wzt.tnn.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCase;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wzt.tnn.R;
import com.wzt.tnn.model.BoxInfo;
import com.wzt.tnn.model.NanoDet;
import com.wzt.tnn.model.YOLOv5;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class LiveActivity extends AppCompatActivity {
    public static int YOLOV5S = 1;
    public static int NANODET = 2;

    public static int USE_MODEL = YOLOV5S;
    public static boolean USE_GPU = false;

    public static CameraX.LensFacing CAMERA_ID = CameraX.LensFacing.BACK;

    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_PICK_VIDEO = 3;
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private Toolbar toolbar;
    private ImageView resultImageView;
    private TextView tvNMS;
    private TextView tvThreshold;
    private SeekBar nmsSeekBar;
    private SeekBar thresholdSeekBar;
    private TextView tvNMNThreshold;
    private TextView tvInfo;
    private double threshold = 0.3, nms_threshold = 0.7;
    private TextureView viewFinder;

    protected float videoSpeed = 1.0f;
    protected long videoCurFrameLoc = 0;
    public static int VIDEO_SPEED_MAX = 20 + 1;
    public static int VIDEO_SPEED_MIN = 1;

    private AtomicBoolean detectCamera = new AtomicBoolean(false);
    private AtomicBoolean detectPhoto = new AtomicBoolean(false);
    private AtomicBoolean detectVideo = new AtomicBoolean(false);

    private long startTime = 0;
    private long endTime = 0;
    private int width;
    private int height;

    double total_fps = 0;
    int fps_count = 0;

    protected Bitmap mutableBitmap;

    ExecutorService detectService = Executors.newSingleThreadExecutor();

    FFmpegMediaMetadataRetriever mmr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
            finish();
        }

        initModel();
        initViewID();
        initViewListener();

    }

    protected void initViewListener() {
        toolbar.setNavigationIcon(R.mipmap.actionbar_dark_back_icon);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (USE_MODEL != YOLOV5S && USE_MODEL != NANODET) {
            nmsSeekBar.setEnabled(false);
            thresholdSeekBar.setEnabled(false);
            tvNMS.setVisibility(View.GONE);
            tvThreshold.setVisibility(View.GONE);
            nmsSeekBar.setVisibility(View.GONE);
            thresholdSeekBar.setVisibility(View.GONE);
            tvNMNThreshold.setVisibility(View.GONE);
        } else if (USE_MODEL == YOLOV5S || USE_MODEL == NANODET) {
            threshold = 0.4f;
            nms_threshold = 0.45f;
        }

        nmsSeekBar.setProgress((int) (nms_threshold * 100));
        thresholdSeekBar.setProgress((int) (threshold * 100));
        final String format = "THR: %.2f, NMS: %.2f";
        tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
        nmsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                nms_threshold = i / 100.f;
                tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                threshold = i / 100.f;
                tvNMNThreshold.setText(String.format(Locale.ENGLISH, format, threshold, nms_threshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        resultImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (detectVideo.get() || detectPhoto.get()) {
                    detectPhoto.set(false);
                    detectVideo.set(false);
                    startCamera();
                }
            }
        });

        viewFinder.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                updateTransform();
            }
        });

        viewFinder.post(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });
    }

    protected void initViewID() {
        toolbar = findViewById(R.id.tool_bar);
        resultImageView = findViewById(R.id.imageView);
        tvNMNThreshold = findViewById(R.id.valTxtView);
        tvInfo = findViewById(R.id.tv_info);
        tvNMS = findViewById(R.id.txtNMS);
        tvThreshold = findViewById(R.id.txtThresh);
        nmsSeekBar = findViewById(R.id.nms_seek);
        thresholdSeekBar = findViewById(R.id.threshold_seek);
        viewFinder = findViewById(R.id.view_finder);
    }

    protected void initModel() {
        String path = this.getFilesDir() + File.separator;
        if (USE_MODEL == YOLOV5S) {
            YOLOv5.init(InitActivity.YOLOV5S_TNN[0], InitActivity.YOLOV5S_TNN[1], path, USE_GPU);
        } else if (USE_MODEL == NANODET) {
            NanoDet.init(InitActivity.NANODET_TNN[0], InitActivity.NANODET_TNN[1], path, USE_GPU);
        }
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();
        // Compute the center of the view finder
        float centerX = viewFinder.getWidth() / 2f;
        float centerY = viewFinder.getHeight() / 2f;
        float[] rotations = {0, 90, 180, 270};
        // Correct preview output to account for display rotation
        float rotationDegrees = rotations[viewFinder.getDisplay().getRotation()];
        matrix.postRotate(-rotationDegrees, centerX, centerY);
        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix);
    }

    private void startCamera() {
        CameraX.unbindAll();
        // 1. preview
        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .setLensFacing(CAMERA_ID)
//                .setTargetAspectRatio(Rational.NEGATIVE_INFINITY)  // 宽高比
                .setTargetResolution(new Size(480, 640))  // 分辨率
                .build();

        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener() {
            @Override
            public void onUpdated(Preview.PreviewOutput output) {
                ViewGroup parent = (ViewGroup) viewFinder.getParent();
                parent.removeView(viewFinder);
                parent.addView(viewFinder, 0);

                viewFinder.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });
        DetectAnalyzer detectAnalyzer = new DetectAnalyzer();
        CameraX.bindToLifecycle((LifecycleOwner) this, preview, gainAnalyzer(detectAnalyzer));

    }


    private UseCase gainAnalyzer(DetectAnalyzer detectAnalyzer) {
        ImageAnalysisConfig.Builder analysisConfigBuilder = new ImageAnalysisConfig.Builder();
        analysisConfigBuilder.setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE);
        analysisConfigBuilder.setTargetResolution(new Size(480, 640));  // 输出预览图像尺寸
        ImageAnalysisConfig config = analysisConfigBuilder.build();
        ImageAnalysis analysis = new ImageAnalysis(config);
        analysis.setAnalyzer(detectAnalyzer);
        return analysis;
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        byte[] nv21 = imagetToNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private byte[] imagetToNV21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ImageProxy.PlaneProxy y = planes[0];
        ImageProxy.PlaneProxy u = planes[1];
        ImageProxy.PlaneProxy v = planes[2];
        ByteBuffer yBuffer = y.getBuffer();
        ByteBuffer uBuffer = u.getBuffer();
        ByteBuffer vBuffer = v.getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private class DetectAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(ImageProxy image, final int rotationDegrees) {
            detectOnModel(image, rotationDegrees);
        }
    }

    private void detectOnModel(ImageProxy image, final int rotationDegrees) {
        if (detectCamera.get() || detectPhoto.get() || detectVideo.get()) {
            return;
        }
        detectCamera.set(true);
        startTime = System.currentTimeMillis();
        final Bitmap bitmapsrc = imageToBitmap(image);  // 格式转换
//        byte[] nv21 = imagetToNV21(image);
        if (detectService == null) {
            detectCamera.set(false);
            return;
        }
        detectService.execute(new Runnable() {
            @Override
            public void run() {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                width = bitmapsrc.getWidth();
                height = bitmapsrc.getHeight();
                Bitmap bitmap = Bitmap.createBitmap(bitmapsrc, 0, 0, width, height, matrix, false);
//                Log.d("wzt_", bitmap.getWidth() + "x" + bitmap.getHeight());

                detectAndDraw(bitmap);
                showResultOnUI();
            }
        });
    }

    protected void showResultOnUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                detectCamera.set(false);
                resultImageView.setImageBitmap(mutableBitmap);
                endTime = System.currentTimeMillis();
                long dur = endTime - startTime;
                float fps = (float) (1000.0 / dur);
                total_fps = (total_fps == 0) ? fps : (total_fps + fps);
                fps_count++;
                String modelName = getModelName();

                tvInfo.setText(String.format(Locale.CHINESE,
                        "%s\nSize: %dx%d\nTime: %.3f s\nFPS: %.3f\nAVG_FPS: %.3f",
                        modelName, height, width, dur / 1000.0, fps, (float) total_fps / fps_count));
            }
        });
    }


    protected Bitmap drawBoxRects(Bitmap mutableBitmap, BoxInfo[] results) {
        if (results == null || results.length <= 0) {
            return mutableBitmap;
        }
        Canvas canvas = new Canvas(mutableBitmap);
        final Paint boxPaint = new Paint();
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4 * mutableBitmap.getWidth() / 800.0f);
        boxPaint.setTextSize(30 * mutableBitmap.getWidth() / 800.0f);
        for (BoxInfo box : results) {
            boxPaint.setColor(box.getColor());
            boxPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(box.getLabel() + String.format(Locale.CHINESE, " %.3f", box.getScore()), box.x0 + 3, box.y0 + 30 * mutableBitmap.getWidth() / 1000.0f, boxPaint);
            boxPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(box.getRect(), boxPaint);
        }
        return mutableBitmap;
    }

    protected Bitmap detectAndDraw(Bitmap bitmap) {
        byte[] imageDataBytes = null;
        BoxInfo[] result = null;
        if (USE_MODEL == YOLOV5S) {
            result = YOLOv5.detect(bitmap, imageDataBytes, bitmap.getWidth(), bitmap.getHeight(), threshold, nms_threshold);
        } else if (USE_MODEL == NANODET) {
            result = NanoDet.detect(bitmap, imageDataBytes, bitmap.getWidth(), bitmap.getHeight(), threshold, nms_threshold);
        }
        if (result == null) {
            detectCamera.set(false);
            mutableBitmap = bitmap;
            return bitmap;
        }
        if (USE_MODEL == YOLOV5S || USE_MODEL == NANODET) {
            mutableBitmap = drawBoxRects(bitmap, result);
        }
        return mutableBitmap;
    }

    protected String getModelName() {
        String modelName = "ohhhhh";
        if (USE_MODEL == YOLOV5S) {
            modelName = "YOLOv5s";
        } else if (USE_MODEL == NANODET) {
            modelName = "NanoDet";
        }
        return USE_GPU ? "[ GPU ] " + modelName : "[ CPU ] " + modelName;
    }

    @Override
    protected void onDestroy() {
        detectCamera.set(false);
        detectVideo.set(false);
        if (detectService != null) {
            detectService.shutdown();
            detectService = null;
        }
        if (mmr != null) {
            mmr.release();
        }
        CameraX.unbindAll();
        super.onDestroy();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera Permission!", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }


}