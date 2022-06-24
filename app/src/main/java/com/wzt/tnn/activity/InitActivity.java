package com.wzt.tnn.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wzt.tnn.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class InitActivity extends AppCompatActivity {

    public static final String YOLOV5S_TNN[] = {"yolov5s_sim_opt.tnnproto", "yolov5s_sim_opt.tnnmodel"};
    public static final String NANODET_TNN[] = {"nanodet_sim_opt.tnnproto", "nanodet_sim_opt.tnnmodel"};
    private static final String[][] models = {YOLOV5S_TNN, NANODET_TNN};

    private Button btnYOLOv5s;

    private boolean useGPU = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        findView();
        initView();
        copyModelFromAssetsToData();
    }

    protected void copyModelFromAssetsToData() {
        // assets目录下的模型文件名
        Toast.makeText(this, "Copy model to data...", Toast.LENGTH_SHORT).show();
        try {
            for (String[] tnn_model : models) {
                for (String x : tnn_model) {
                    copyAssetFileToFiles(this, x);
                }
            }
            enableButtons();
            Toast.makeText(this, "Copy model Success", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Copy model Error", Toast.LENGTH_SHORT).show();
        }
    }

    protected void findView() {
        btnYOLOv5s = findViewById(R.id.btn_start_detect1);

        btnYOLOv5s.setEnabled(false);
    }

    private void enableButtons() {
        btnYOLOv5s.setEnabled(true);
    }

    protected void initView() {
        btnYOLOv5s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LiveActivity.USE_MODEL = LiveActivity.YOLOV5S;
                Intent intent = new Intent(InitActivity.this, LiveActivity.class);
                InitActivity.this.startActivity(intent);
            }
        });

    }

    public void copyAssetDirToFiles(Context context, String dirname) throws IOException {
        File dir = new File(context.getFilesDir() + File.separator + dirname);
        dir.mkdir();

        AssetManager assetManager = context.getAssets();
        String[] children = assetManager.list(dirname);
        for (String child : children) {
            child = dirname + File.separator + child;
            String[] grandChildren = assetManager.list(child);
            if (0 == grandChildren.length) {
                copyAssetFileToFiles(context, child);
            } else {
                copyAssetDirToFiles(context, child);
            }
        }
    }

    public void copyAssetFileToFiles(Context context, String filename) throws IOException {
        InputStream is = context.getAssets().open(filename);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();

        File of = new File(context.getFilesDir() + File.separator + filename);
        of.createNewFile();
        FileOutputStream os = new FileOutputStream(of);
        os.write(buffer);
        os.close();
    }

}