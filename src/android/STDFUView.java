/**
 */
package com.vensi.STDFUPlugin;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class STDFUView {
    private Activity mParentActivity;

    ProgressBar mProgressbar;
    TextView mProgressLabel;
    TextView mStatusLabel;
    RelativeLayout mProgress;
    RelativeLayout mOverlay;

    AlertDialog mProgressAlertView;

    public STDFUView( Activity parentActivity) {
        mParentActivity = parentActivity;
    }

    public void showOverlay(final FrameLayout parent, final String overlayText) {
        mParentActivity.runOnUiThread(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                initializeOverlay();

                RelativeLayout.LayoutParams relativeLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);

                mStatusLabel = new TextView(mParentActivity.getApplicationContext());
                mStatusLabel.setText(overlayText);
                mStatusLabel.setTextColor(Color.WHITE);
                mStatusLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                mStatusLabel.setGravity(Gravity.CENTER_VERTICAL);
                mStatusLabel.setLayoutParams(relativeLayoutParams);

                mOverlay.addView(mStatusLabel);

                parent.addView(mOverlay);
            }
        });
    }

    public void showProgressView(final FrameLayout parent) {
        mParentActivity.runOnUiThread(new Runnable() {
            @SuppressLint("NewApi")
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
            @Override
            public void run() {
                initializeOverlay(); // Just in case it's been null-ed.

                RelativeLayout popup = new RelativeLayout(mParentActivity.getApplicationContext());
                RelativeLayout.LayoutParams relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                relativeLayoutparams.setMargins(0, 0, 0, 0);
                popup.setLayoutParams(relativeLayoutparams);
                popup.setClickable(false);

                GradientDrawable border = new GradientDrawable();
                border.setStroke(5, Color.WHITE);
                border.setColor(Color.WHITE);
                popup.setBackground(border);

                mProgressLabel = new TextView(mParentActivity.getApplicationContext());
                mProgressLabel.setText("0%");
                mProgressLabel.setTextColor(Color.BLACK);
                mProgressLabel.setGravity(Gravity.CENTER_HORIZONTAL);
                mProgressLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,60);
                relativeLayoutparams.setMargins(0, 160, 0, 0);
                mProgressLabel.setLayoutParams(relativeLayoutparams);

                TextView statusLabel = new TextView(mParentActivity.getApplicationContext());
                statusLabel.setText("Updating");
                statusLabel.setTextColor(Color.BLACK);
                statusLabel.setGravity(Gravity.CENTER_HORIZONTAL);
                statusLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,60);
                relativeLayoutparams.setMargins(0, 30, 0, 0);
                statusLabel.setLayoutParams(relativeLayoutparams);

                mProgressbar = new ProgressBar(mParentActivity.getApplicationContext(), null, android.R.attr.progressBarStyleHorizontal);
                relativeLayoutparams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);
                relativeLayoutparams.setMargins(30, 100, 30, 0);
                mProgressbar.setLayoutParams(relativeLayoutparams);
                mProgressbar.getIndeterminateDrawable().setColorFilter(Color.DKGRAY, PorterDuff.Mode.MULTIPLY);
                mProgressbar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY);

                popup.addView(statusLabel);
                popup.addView(mProgressbar);
                popup.addView(mProgressLabel);

                AlertDialog.Builder builder = new AlertDialog.Builder(mParentActivity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                builder.setCancelable(false);
                builder.setView(popup);

                mProgressAlertView = builder.create();

                mProgressAlertView.show();


                mOverlay.removeAllViews();
            }
        });
    }

    public void updateProgressView(final long value) {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressLabel.setText(value + "%");
                mProgressbar.setProgress((int)value);
            }
        });
    }

    public void removeOverlay(final FrameLayout parent)  {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mOverlay.removeAllViews();
                parent.removeView(mOverlay);
                mProgressAlertView.dismiss();
            }
        });
    }

    private void initializeOverlay() {
        if (mOverlay == null) {
            FrameLayout.LayoutParams layoutparams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutparams.setMargins(0, 0, 0, 0);

            mOverlay = new RelativeLayout(mParentActivity.getApplicationContext());
            mOverlay.setBackgroundColor(Color.argb(200, 50, 50, 50));
            mOverlay.setLayoutParams(layoutparams);
        }
    }

}
