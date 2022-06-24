// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package com.wzt.tnn.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.wzt.tnn.R;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements Runnable {
    private int mImageIndex = 0;
    private String[] mTestImages = {"litest1.jpg", "litest2.jpg","litest4.jpg", "litest5.jpg", "litest6.jpg","litest7.jpg", "litest8.jpg","litest9.jpg"};

    private ImageView mImageView;
    private ResultView mResultView;
    private Button mButtonDetect;
    private TextView viewAnalysis;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;
    private Module mModule = null;
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        setContentView(R.layout.activity_main);

        try {
            mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }

        mImageView = findViewById(R.id.imageView);
        mImageView.setImageBitmap(mBitmap);
        viewAnalysis = findViewById(R.id.analysisView);
        viewAnalysis.setText("Show detection analysis result");
        viewAnalysis.setMovementMethod(ScrollingMovementMethod.getInstance());
        mResultView = findViewById(R.id.resultView);
        mResultView.setVisibility(View.INVISIBLE);

        final Button buttonSave = findViewById(R.id.saveButton);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImageView(getViewBitmap(mImageView));
            }
        });

        final Button buttonTest = findViewById(R.id.testButton);
        buttonTest.setText(("Test Image 1/n"));
        buttonTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                mImageIndex = (mImageIndex + 1) % mTestImages.length;
                buttonTest.setText(String.format("Text Image %d/%d", mImageIndex + 1, mTestImages.length));

                try {
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
                    mImageView.setImageBitmap(mBitmap);
                    viewAnalysis.setText("Show detection analysis result");
                } catch (IOException e) {
                    Log.e("Object Detection", "Error reading assets", e);
                    finish();
                }
            }
        });


        final Button buttonSelect = findViewById(R.id.selectButton);
        buttonSelect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);

                final CharSequence[] options = { "Choose from Photos", "Take Picture", "Cancel" };
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("New Test Image");

                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (options[item].equals("Take Picture")) {
                            Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                            startActivityForResult(takePicture, 0);
                        }
                        else if (options[item].equals("Choose from Photos")) {
                            Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                            startActivityForResult(pickPhoto , 1);
                        }
                        else if (options[item].equals("Cancel")) {
                            dialog.dismiss();
                        }
                    }
                });
                builder.show();
            }
        });

        final Button buttonLive = findViewById(R.id.liveButton);
        buttonLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LiveActivity.USE_MODEL = LiveActivity.YOLOV5S;
                Intent intent = new Intent(MainActivity.this, LiveActivity.class);
                //final Intent intent = new Intent(MainActivity.this,ObjectDetectionActivity.class);
                startActivity(intent);
            }
        });

//        final Button buttonRecord=findViewById(R.id.recordButton);
//        buttonRecord.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                final Intent intent = new Intent(MainActivity.this, RecorderActivity.class);
//                startActivity(intent);
//            }
//        });

        mButtonDetect = findViewById(R.id.detectButton);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonDetect.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonDetect.setText(getString(R.string.run_model));

                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;

                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        try {
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "libest.torchscript.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("liclasses.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
    }
    //——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    private class SaveObservable implements Observable.OnSubscribe<String> {

        private Bitmap drawingCache = null;

        public SaveObservable(Bitmap drawingCache) {
            this.drawingCache = drawingCache;
        }

        @Override
        public void call(Subscriber<? super String> subscriber) {
            if (drawingCache == null) {
                subscriber.onError(new NullPointerException("imageview的bitmap获取为null,请确认imageview显示图片了"));
            } else {
                try {
                    File imageFile = new File(Environment.getExternalStorageDirectory(), "saveImageview.jpg");
                    FileOutputStream outStream;
                    outStream = new FileOutputStream(imageFile);
                    drawingCache.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
                    subscriber.onNext(Environment.getExternalStorageDirectory().getPath());
                    subscriber.onCompleted();
                    outStream.flush();
                    outStream.close();
                } catch (IOException e) {
                    subscriber.onError(e);
                }
            }
        }

    }

    private class SaveSubscriber extends Subscriber<String> {

        @Override
        public void onCompleted() {
            Toast.makeText(getApplicationContext(), "保存成功", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(Throwable e) {
            Log.i(getClass().getSimpleName(), e.toString());
            Toast.makeText(getApplicationContext(), "保存失败——> " + e.toString(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onNext(String s) {
            Toast.makeText(getApplicationContext(), "保存路径为：-->  " + s, Toast.LENGTH_SHORT).show();
        }
    }


    private void saveImageView(Bitmap drawingCache) {
        Observable.create(new SaveObservable(drawingCache))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SaveSubscriber());
    }

    /**
     * 某些机型直接获取会为null,在这里处理一下防止国内某些机型返回null
     */
    private Bitmap getViewBitmap(View view) {
        if (view == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    //——————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90.0f);
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                        viewAnalysis.setText("Show detection analysis result");
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90.0f);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                viewAnalysis.setText("Show detection analysis result");
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void run() {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        final ArrayList<Result> results =  PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);
        HashMap<Integer,Integer> myResult=new HashMap<>();
        for(int i=0;i<results.size();++i){
            int classindex=results.get(i).classIndex;
            myResult.put(classindex,myResult.getOrDefault(classindex,0)+1);
        }
        List<Map.Entry<Integer,Integer>> list=new ArrayList<>(myResult.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
            @Override
            public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                return o2.getValue()-o1.getValue();   //表示将map中的值按照降序进行排序
            }
        });
        StringBuffer resAnalysis=new StringBuffer();
        resAnalysis.append("The detection analysis result is:");
        resAnalysis.append("\n");
        Iterator<Map.Entry<Integer, Integer>> iter = list.iterator();
        Map.Entry entry = iter.next();
        int key = (int) entry.getKey();
        if(key==0)
            resAnalysis.append("白菜炭疽病：\n" +
                    "大白菜炭疽病是由希金斯刺盘孢引起的，主要危害叶片和叶柄，也能危害花梗和种荚。病初期叶片上出现白色水渍状斑点，逐渐扩大为灰褐色圆斑，病斑边缘微凸起，后期斑块中央成半透明状，叶脉病斑为褐色长椭圆形并明显凹陷。\n" +
                    "农业防治：\n" +
                    "（1）茬口轮作：提倡与非十字花科蔬菜隔年轮作，减少田间病菌来源。\n" +
                    "（2）加强田间管理：合理密植，开好排水沟系，防治雨后积水引起病害。\n" +
                    "（3）收获后及时清除残体，深翻土壤，加速病残体的腐烂分解。\n" +
                    "化学防治：\n" +
                    "（1）种子消毒：在播前要做好种子消毒，干种子用2.5%咯菌腈悬浮种衣剂包衣，包衣使用剂量为3-4‰，包衣后晾干播种。\n" +
                    "（2）药剂防治：在发病初期开始喷药，每隔7-10天喷1次，连续喷2-3次，重病田视病情发展还要增加喷药次数。药剂可选用250克/升嘧菌酯悬浮剂800-1000倍液，15%咪鲜胺微乳剂（胜炭）1000-1500倍液（每亩用量30-50克）。");
        else if(key==1)
            resAnalysis.append("番茄肌腐病：\n" +
                    "番茄脐腐病主要是因为生理障碍（无病原菌），是番茄遇不适的环境条件时常见的生理失调症。番茄脐腐病仅发生在番茄果实上，青果期至着色期前最易发病，发病初始在幼果脐部及其附近产生水浸状斑，暗绿色，后扩大为暗褐色大斑。当病部深入到果肉内部时，果肉组织呈干腐状收缩，较坚硬，被害部分外部呈扁平状，表面皱缩，病果一般不腐烂。后期遇湿度高时，病部极易被其他腐生霉菌寄生，在病部出现黑褐色或其他颜色霉状物，造成病果软化腐烂。\n" +
                    "农业防治：\n" +
                    "（1）科学施肥：补充钙素、施足基肥、合理配合。土壤缺钙时，每亩用消石灰或碳酸钙50千克均匀撒于地面并翻入耕层中。适量增加追肥中的氮肥，并降低基肥氮的使用率，在番茄施氮时，若纯氮施用超过30千克/亩，就会导致脐腐病发病严重，所以控制氮肥用量是番茄增产的关键。\n" +
                    "（2）根外追施钙肥技术：番茄结果后1个月，是吸收钙的黄金时期。这时可喷洒1%的过磷酸钙，或者选用0.5%氯化钙加5毫克/千克萘乙酸、0.1%硝酸钙及爱多收6000倍液。每次间隔15天，连续喷洒2-3次。\n" +
                    "（3）控制温、湿度：应防止土壤干湿不定，切忌土壤过分干旱。\n" +
                    "（4）田间管理：中耕时松土，且施草木灰200千克/亩，应施于7-8厘米土层内，以促进土壤透气、透水，增强植株抗病性。\n" +
                    "化学防治：\n" +
                    "一般在发病时，选择1%的过磷酸钙或0.1%氯化钙进行根外施肥，每周1次，值得注意的是，喷药时一定要接触叶片及果实，尤其是果实。");
        else if(key==2)
            resAnalysis.append("番茄叶片斑潜蝇:\n" +
                    "番茄斑潜蝇别名蔬菜斑潜蝇，是番茄的一个重要虫害，幼虫孵化后潜食叶肉，呈曲折婉蜒的食痕，苗期2-7叶受害多，严重的潜痕密布，致叶片发黄、枯焦或脱落。\n" +
                    "防治方法：\n" +
                    "（1）施用昆虫生长调节剂类，可影响成虫生殖、卵的孵化和幼虫脱皮、化蛹等。\n" +
                    "（2）提倡施用48%乐斯本乳油1500一2000倍液、1.8%阿巴丁乳油2000倍液、10%烟碱乳油1000倍液、10%除尽悬浮剂1000倍液、5%锐劲特悬浮剂，1500倍液、40%七星宝乳油600一800倍液，在发生高峰期5—7天喷1次，连续防治2—3次。\n" +
                    "（3） 使用番茄斑潜蝇信息素防治。");
        else if(key==3)
            resAnalysis.append("番茄叶片灰霉病：\n" +
                    "番茄灰霉病是由灰葡萄孢引起的、发生在番茄的病害，主要发生在花期和结果期，可危害花、果实、叶片和茎。幼苗染病，子叶先端发黄，叶片呈水溃状腐败；幼茎受害初为水渍状溢缩，继变成褐色病斑。果实发病多从残留的败花和柱头部先被侵染，造成花腐。幼果则全果软腐，果实成熟前病部果皮呈灰白色，水渍状软腐，很快发展成不规则形大斑，果实失水后僵化或湿润软腐。\n" +
                    "农业防治：\n" +
                    "（1）晴天上午及时通风，尤其是大水垄沟灌水的日光温室，灌水后第2～3天，早晨揭帘后打开放风口放风15分钟，再封闭放风口。当日光温室内温度升至30℃，再徐徐打开放风口放风。31℃以上高温可降低病菌孢子的萌发速度，减轻病害的发生。白天日光温室内温度保持20～25℃，当下午温度降至20℃时关闭通风口。夜间温度保持在15～17℃。阴天根据气候及栽培环境要适当放风降湿。\n" +
                    "（2）浇水应在晴天上午进行，防止过量。发病初期适当节制浇水，浇水后注意放风排湿。发病后及时摘除病果、病叶并加以妥善处理，防止病菌飞散传播。果实采收后和在幼苗定植前彻清除病残体，清洁田园减少病菌侵染。\n" +
                    "（3）利用夏秋季节高温，密闭日光温室一周以上，利用太阳光使温室内温度升至70℃以上，高温消毒。\n" +
                    "化学防治：\n" +
                    "在番茄蘸花时，在配好的蘸花稀释液中，按0.1%的用量加入50%腐霉利可湿性粉剂、或50%多霉威可湿性粉剂等预防病菌侵染。番茄定植前，选用50%多菌灵可湿性粉剂500倍液彻底消毒一次，减少病原菌数量。发病初期选用50%速克灵活可湿性粉剂2000倍液进行喷雾防治，每隔7～10天一次，连续2～3次。病严重时，摘除病叶、病果、病茎后，采取以上药剂和办法交替防治2～3次。");
        else if(key==4)
            resAnalysis.append("黄瓜叶片靶板病：\n" +
                    "黄瓜靶斑病是由黄瓜靶斑病菌引起的病害，温暖、高湿发病率会更高。病斑起初为黄色水浸状斑点，中期扩大为圆形或不规则形，易穿孔，叶正面病斑粗糙不平，病斑整体褐色，中央灰白色、半透明。后期病斑直径可达10~15毫米，病斑中央有一明显的眼状靶心，湿度大时病斑上可生有稀疏灰黑色霉状物，呈环状。\n" +
                    "防治方法：\n" +
                    "（1）与非瓜类作物实行2～3年以上轮作，彻底清除前茬作物病残体，减少初侵染源，同时喷施消毒药剂加新高脂膜进行消毒处理。\n" +
                    "（2）及时摘除中下部病斑较多的病叶，减少病原菌数量。\n" +
                    "（3）靶斑病多发生在结瓜盛期，应及时冲施含有芸薹素内酯的碧禾冲施肥，叶面喷施斯德考普叶面肥，及时摘除大瓜，促进植株迅速长秧，长新叶。\n" +
                    "（4）放风排湿，改善通风透气性能。");
        if(list.size()==0)
            resAnalysis.append("No disease was detected.");

        runOnUiThread(() -> {
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mResultView.setResults(results);
            mResultView.invalidate();
            mResultView.setVisibility(View.VISIBLE);
            viewAnalysis.setText(resAnalysis);
        });
    }
}
