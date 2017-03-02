//
//  STDFUView.m
//
//
//  Created by Ronald Garay on 3/1/17.
//
//


#import "STDFUView.h"


@implementation STDFUView

- (id) init {
    CGRect screenRect = [[UIScreen mainScreen] bounds];
    return [self initWithFrame: CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
}

- (id) initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame: frame]) {
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        overlayView = [[UIView alloc] initWithFrame:CGRectMake(0, 0, screenRect.size.width, screenRect.size.height)];
        overlayView.tag = 100;
        [overlayView setBackgroundColor: [UIColor grayColor]];
        overlayView.layer.opacity = 0.9f;
        
        [overlayView addSubview:statusLabel];
        
        [self addSubview: overlayView];
    }
    
    return self;
}

- (void) showOverlay: (NSString *) text {
    dispatch_async(dispatch_get_main_queue(), ^{
        CGRect screenRect = [[UIScreen mainScreen] bounds];
        
        statusLabel = [[UILabel alloc] initWithFrame:CGRectMake(25, 0, screenRect.size.width - 50, screenRect.size.height)];
        [statusLabel setTextColor:[UIColor whiteColor]];
        [statusLabel setTextAlignment:NSTextAlignmentCenter];
        
        if([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPhone) {
            [statusLabel setFont:[UIFont systemFontOfSize:18]];
        } else if([UIDevice currentDevice].userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            [statusLabel setFont:[UIFont systemFontOfSize:22]];
        }
        
        statusLabel.numberOfLines = 5;
        statusLabel.lineBreakMode = NSLineBreakByWordWrapping;
        statusLabel.text =  text;
        
        [overlayView addSubview:statusLabel];
    });
}

- (void) showProgressView {
    dispatch_async(dispatch_get_main_queue(), ^{
        _displayingProgress = YES;
        [statusLabel removeFromSuperview];
        
        popup = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 100, 50)];
        
        progressView = [[UIProgressView alloc] initWithFrame:CGRectMake(10, 5, 250, 10)];
        progressView.progress = 0.0f;
        progressView.tag = 11;
        [progressView setProgressTintColor:[UIColor blackColor]];
        
        progressLabel = [[UILabel alloc] initWithFrame:CGRectMake(120, 30, 120, 20)];
        [progressLabel setTextColor:[UIColor blackColor]];
        progressLabel.tag = 22;
        progressLabel.text = @"0%";
        
        [popup addSubview:progressView];
        [popup addSubview:progressLabel];
        
        progressAlertView = [[UIAlertView alloc] initWithTitle:@"Updating"
                                                       message:@""
                                                      delegate:self
                                             cancelButtonTitle:nil
                                             otherButtonTitles:nil, nil];
        
        [progressAlertView setValue:popup forKey:@"accessoryView"];
        
        [progressAlertView show];
    });
}

- (void) updateProgressView: (float) value {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSInteger intValue = (int)(value * 100);
        
        progressLabel.text = [NSString stringWithFormat:@"%ld%%", (long)intValue];
        [progressView setProgress: value animated:YES];
    });
}

- (void) removeOverlay {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (_displayingProgress == YES) {
            [progressAlertView dismissWithClickedButtonIndex:0 animated:YES];
        }
        
        [overlayView removeFromSuperview];
    });
}


@end
