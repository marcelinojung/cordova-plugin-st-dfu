//
//  STDFUView.h
//
//
//  Created by Ronald Garay on 3/1/17.
//
//

#import <UIKit/UIKit.h>

@interface STDFUView : UIView {
    UIView *popup;
    UIView *overlayView;
    UILabel *statusLabel;
    UILabel *progressLabel;
    UIProgressView *progressView;
    UIAlertView *progressAlertView;
}

@property (nonatomic, assign) BOOL displayingProgress;

- (void) showOverlay: (NSString *) text;
- (void) showProgressView;
- (void) updateProgressView: (float) value;
- (void) removeOverlay;

@end
