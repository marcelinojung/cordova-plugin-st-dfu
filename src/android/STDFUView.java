/**
 */
package com.vensi.STDFUPlugin;

import android.app.Activity;
import android.widget.FrameLayout;

public class STDFUView {
    private FrameLayout mParentLayout;
    private Activity mParentActivity;

    public STDFUView(FrameLayout parentLayout, Activity parentActivity) {
        mParentLayout = parentLayout;
        mParentActivity = parentActivity;
//        CGRect screenRect =[[UIScreen mainScreen]bounds];
//        return[self initWithFrame:CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
    }

//    -(id)initWithFrame:(CGRect)frame
//
//    {
//        if (self =[super initWithFrame:
//    frame]){
//        CGRect screenRect =[[UIScreen mainScreen]bounds];
//        overlayView =[[UIView alloc]initWithFrame:
//        CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
//        overlayView.tag = 100;
//        [overlayView setBackgroundColor:[UIColor grayColor]];
//        overlayView.layer.opacity = 0.9f;
//
//        [overlayView addSubview:statusLabel];
//
//        [self addSubview:overlayView];
//    }
//
//        return self;
//}

    public void showOverlay() {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                CGRect screenRect =[[UIScreen mainScreen]bounds];
//
//                statusLabel =[[UILabel alloc]initWithFrame:
//                CGRectMake(25, 0, screenRect.size.width - 50, screenRect.size.height)];
//                [statusLabel setTextColor:[UIColor whiteColor]];
//                [statusLabel setTextAlignment:NSTextAlignmentCenter];
//
//                if ([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPhone){
//                    [statusLabel setFont:[UIFont systemFontOfSize:18]];
//                }else if ([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPad){
//                    [statusLabel setFont:[UIFont systemFontOfSize:22]];
//                }
//
//                statusLabel.numberOfLines = 5;
//                statusLabel.lineBreakMode = NSLineBreakByWordWrapping;
//                statusLabel.text = text;
//
//                [overlayView addSubview:statusLabel];
            }
        });
    }

    public void showProgressView() {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                _displayingProgress = YES;
//                [statusLabel removeFromSuperview];
//
//                popup =[[UIView alloc]initWithFrame:
//                CGRectMake(0, 0, 100, 50)];
//
//                progressView =[[UIProgressView alloc]initWithFrame:
//                CGRectMake(10, 5, 250, 10)];
//                progressView.progress = 0.0f;
//                progressView.tag = 11;
//                [progressView setProgressTintColor:[UIColor blackColor]];
//
//                progressLabel =[[UILabel alloc]initWithFrame:
//                CGRectMake(120, 30, 120, 20)];
//                [progressLabel setTextColor:[UIColor blackColor]];
//                progressLabel.tag = 22;
//                progressLabel.text = @ "0%";
//
//                [popup addSubview:progressView];
//                [popup addSubview:progressLabel];
//
//                progressAlertView =[[UIAlertView alloc]initWithTitle:
//                @ "Updating"
//                message:
//                @ ""
//                delegate:
//                self
//                cancelButtonTitle:
//                nil
//                otherButtonTitles:
//                nil, nil];
//
//                [progressAlertView setValue:popup forKey:@ "accessoryView"];
//
//                [progressAlertView show];
            }
        });
    }

    public void updateProgressView(float value) {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//        NSInteger intValue = (int) (value * 100);
//
//        progressLabel.text =[NSString stringWithFormat:@ "%ld%%", (long) intValue];
//        [progressView setProgress:value animated:YES];
            }
        });
    }

    public void removeOverlay()  {
        mParentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                if (_displayingProgress == YES) {
//                    [progressAlertView dismissWithClickedButtonIndex:0 animated:
//                    YES];
//                }
//
//                [overlayView removeFromSuperview];
            }
        });
    }

}
