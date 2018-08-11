package com.fiserv.fcf.ui;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.fiserv.fcf.R;
import com.fiserv.fcf.classifier.Classifier;
import com.fiserv.fcf.classifier.Result;
import com.fiserv.fcf.utils.ImageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class DemoActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private TextView mTvPrediction;
    private TextView mTvProbability;
    private TextView mTvTimeCost;
    private Button mDetect;
    private Button mLoad;
    private ImageView mImage;
    private Bitmap mSample;
    private Classifier mClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        mTvPrediction = (TextView)findViewById(R.id.tv_prediction);
        mTvProbability = (TextView) findViewById(R.id.tv_probability);
        mTvTimeCost = (TextView) findViewById(R.id.tv_timecost);
        mImage = (ImageView) findViewById(R.id.iv_image);
        mDetect = (Button) findViewById(R.id.btn_detect);
        mDetect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mClassifier == null) {
                    Log.e(LOG_TAG, "onDetectClick(): Classifier is not initialized");
                    return;
                }
                Bitmap inverted = ImageUtil.invert(mSample);
                Result result = mClassifier.classify(Bitmap.createScaledBitmap(inverted, 28, 28, true));
                renderResult(result);

            }
        });

        mLoad = (Button) findViewById(R.id.btn_load);
        mLoad.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mTvPrediction.setText(R.string.empty);
                mTvProbability.setText(R.string.empty);
                mTvTimeCost.setText(R.string.empty);
                mSample = null;
                AssetManager assetManager = getAssets();
                InputStream input = null;
                try {
                    int random = new Random().nextInt(26);
                    String imageName = String.valueOf(random) + ".png";
                    input = assetManager.open(imageName);
                    mSample = BitmapFactory.decodeStream(input);
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Failed to read image asset");
                } finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                    } catch (IOException e) {
                        Log.d(LOG_TAG, "Failed to close inputstream");
                    }
                }

                if (mSample != null) {
                    mImage.setImageBitmap(mSample);
                }
            }
        });
        init();
    }

    private void init() {
        try {
            mClassifier = new Classifier(this);
        } catch (IOException e) {
            Toast.makeText(this, R.string.failed_to_create_classifier, Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "init(): Failed to create tflite model", e);
        }
    }

    private void renderResult(Result result) {
        mTvPrediction.setText(String.valueOf(result.getLetter()));
        mTvProbability.setText(String.valueOf(result.getProbability()));
        mTvTimeCost.setText(String.format(getString(R.string.timecost_value),
                result.getTimeCost()));
    }

}
