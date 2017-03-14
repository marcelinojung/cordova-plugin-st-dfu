//
//  STDFUPlugin.m
//
//
//  Created by Ronald Garay on 3/1/17.
//
//

#import "STDFUPlugin.h"
#import "Reachability.h"
#import "STDFUView.h"

#import <Cordova/CDVAvailability.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <BlueSTDFU/BlueSTSDK.h>
#import <BlueSTDFU/BlueSTSDKFwUpgradeConsole.h>

@interface STDFUPlugin ()<BlueSTSDKFwUpgradeReadVersionDelegate, BlueSTSDKFwUpgradeUploadFwDelegate, BlueSTSDKNodeStateDelegate, BlueSTSDKManagerDelegate, UIAlertViewDelegate>
{
    
}
@end

@implementation STDFUPlugin
{
    STDFUView *mUpdateView;
    
    BlueSTSDKFwUpgradeConsole *mConsole;
    BlueSTSDKNode *mNode;
    BlueSTSDKManager *mSTManager;
    
    NSUInteger mFwFileLength;
    
    NSString *mCurrentCallbackId;
    NSString *mCurrentDeviceUUID;
    NSString *mCurrentDeviceName;
}

- (void) pluginInitialize {
    mSTManager = [BlueSTSDKManager sharedInstance]; // We need this to interact via BlueSTSDK.
    [mSTManager addDelegate: self];
    
    mUpdateView = [[STDFUView alloc] init];
    mUpdateView.hidden = YES;
    
    [self.webView addSubview: mUpdateView];
}

- (void) checkUpdate:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        mCurrentCallbackId = command.callbackId; // Store callback ID for result message
        
        if ([command.arguments count] > 0) {
            mCurrentDeviceUUID = [command.arguments objectAtIndex:0]; // Get the UUID of device so we can extract version number from name.
            
            CBPeripheral *peripheral = [self getPeripheralWithUUID: [[NSUUID alloc] initWithUUIDString:mCurrentDeviceUUID]];
            
            if (peripheral != nil) {
                NSInteger version = [self extractIntegerWithRegEx: @"[0-9][0-9][0-9]"
                                                       fromString: peripheral.name];
                
                NSInteger newVersion = [self downloadVersion];
                if (version < newVersion) { //TODO: Need to compare with version.
                    dispatch_async(dispatch_get_main_queue(), ^{
                        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"" message:@"New update available. Would you like to update your device?" delegate:self cancelButtonTitle:@"NO" otherButtonTitles:@"YES", nil];
                        alert.delegate = self;
                        [alert show];
                    });
                } else {
                    CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                                                messageAsString: @"none"];
                    
                    result.keepCallback = [NSNumber numberWithInt:1];
                    [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
                }
            }
        }
    }];
}

- (void) initiateUpdate:(CDVInvokedUrlCommand *)command {
    [self.commandDelegate runInBackground:^{
        mCurrentCallbackId = command.callbackId;
        [mSTManager discoveryStart];
    }];
}

- (NSURL *) downloadFile {
    Reachability *reachability = [Reachability reachabilityWithHostName:@"www.google.com"];
    if([reachability currentReachabilityStatus] == NotReachable) {
        dispatch_async(dispatch_get_main_queue(), ^{
            UIAlertView *networkAlert = [[UIAlertView alloc] initWithTitle:@"No Network" message:@"There is no network, which is required to update firmware." delegate:self cancelButtonTitle:@"Ok" otherButtonTitles:nil, nil];
            [networkAlert show];
        });
        
        return nil;
    } else {
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *path = [paths objectAtIndex:0];
        
        NSURL *url = [NSURL URLWithString: @"https://s3.amazonaws.com/sttile.blueapp.io/test/BlueMS2_ST.bin"];
        NSData *urlData = [NSData dataWithContentsOfURL:url];
        
        mFwFileLength = [urlData length];
        
        if (mFwFileLength > 0) {
            //Find a cache directory. You could consider using documenets dir instead (depends on the data you are fetching)
            NSLog(@"Got the data!");
            
            //Save the data
            NSLog(@"Saving");
            NSString *dataPath = [path stringByAppendingPathComponent:@"BlueMS2_ST.bin"];
            dataPath = [dataPath stringByStandardizingPath];
            [urlData writeToFile:dataPath atomically:YES];
            
            return [NSURL URLWithString: dataPath];
        }
    }
    
    return nil;
}

- (NSInteger) downloadVersion {
    NSInteger version = 0;
    
    NSURL *url = [NSURL URLWithString: @"https://s3.amazonaws.com/sttile.blueapp.io/test/version.json"];
    NSData *data = [NSData dataWithContentsOfURL: url];
    
    if(data != nil) {
        NSError *error;
        NSDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
        version = [[jsonDict valueForKey:@"version"] integerValue];
    }
    
    return version;
}

- (NSInteger) extractIntegerWithRegEx: (NSString *) regexString fromString: (NSString *) string {
    NSError *error;
    NSInteger version = 0;
    
    NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern: regexString options:0 error:&error];
    
    NSTextCheckingResult *match = [regex firstMatchInString:string
                                                    options:0
                                                      range:NSMakeRange(0, [string length])];
    
    if (match != nil) {
        version  = [[string substringWithRange:[match rangeAtIndex:0]] integerValue];
    }
    
    return version;
}

- (CBPeripheral *) getPeripheralWithUUID: (NSUUID *) uuid {
    dispatch_queue_t centralQueue = dispatch_queue_create("com.vensi.centralManager", DISPATCH_QUEUE_SERIAL);
    CBCentralManager *centralManager = [[CBCentralManager alloc] initWithDelegate:nil queue:centralQueue];
    
    if (centralManager != nil) {
        NSArray *devices = [centralManager retrievePeripheralsWithIdentifiers:@[uuid]];
        
        if ([devices count] > 0) {
            for (CBPeripheral *peripheral in devices) {
                if ([[peripheral.identifier UUIDString] isEqualToString: [uuid UUIDString]]) {
                    return peripheral;
                }
            }
        } else {
            // Todo: figure this out
        }
    }
    
    return nil;
}

- (void) updateTimeout {
    [self fwUpgrade: nil onLoadError:nil error: BLUESTSDK_FWUPGRADE_UPLOAD_ERROR_TRANSMISSION];
}

#pragma mark - UIAlertViewDelegate

- (void) alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
    if(buttonIndex == YES) {
        mUpdateView.hidden = NO;
        
        [self.commandDelegate runInBackground:^{
            [mUpdateView showOverlay: @"Preparing device for update. Please wait. This may take 5 minutes."];
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                                        messageAsString: @"approved"];
            
            result.keepCallback = [NSNumber numberWithInt:1];
            [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
        }];
    } else {
        CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                                    messageAsString: @"none"];
        
        result.keepCallback = [NSNumber numberWithInt:1];
        [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
    }
}

#pragma mark - BlueSTSDKFwUpgradeReadVersionDelegate

- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console didVersionRead:(BlueSTSDKFwVersion *)version {
    NSLog(@"didVersionRead");
    NSURL *file;
    
    if(version == nil){
        NSLog(@"Error reading version");
    }
    
    [mUpdateView showProgressView];
    
    file = [self downloadFile];
    
    if (file) {
        [mConsole loadFwFile: file delegate:self];
    } else {
        CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                    messageAsString: @"failed"];
        
        result.keepCallback = [NSNumber numberWithInt:1];
        [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
    }
}

#pragma mark - BlueSTSDKFwUpgradeUploadFwDelegate


- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadProgres:(NSURL *)file loadBytes:(NSUInteger)load {
    NSLog(@"onLoadProgerss");
    if (![mUpdateView displayingProgress]) {
        [mUpdateView showProgressView];
    } else {
        [NSObject cancelPreviousPerformRequestsWithTarget: self selector: @selector(updateTimeout) object: nil];
        
        float progress = (1.0f - load / (float)mFwFileLength);
        
        [mUpdateView updateProgressView: progress];
        
        [self performSelector: @selector(updateTimeout) withObject: nil afterDelay: 5.0f];
    }
}

-(void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadComplite:(NSURL *)file {
    NSLog(@"onLoadComplite");
    
    [mUpdateView removeOverlay];
    mUpdateView.hidden = YES;
    
    CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                                messageAsString: @"updated"];
    
    result.keepCallback = [NSNumber numberWithInt:1];
    [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
}

- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadError:(NSURL *)file error:(BlueSTSDKFwUpgradeUploadFwError)error {
    NSLog(@"onLoadError");
    
    CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                messageAsString: @"failed"];
    
    result.keepCallback = [NSNumber numberWithInt:1];
    [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
    
    [mUpdateView removeOverlay];
    mUpdateView.hidden = YES;
}

#pragma mark - BlueSTSDKNodeStateDelegate

- (void) node:(BlueSTSDKNode *)node didChangeState:(BlueSTSDKNodeState)newState prevState:(BlueSTSDKNodeState)prevState {
    NSLog(@"State changed");
    switch (newState) {
        case BlueSTSDKNodeStateConnected:
            NSLog(@"BlueSTSDKNodeStateConnected");
            mConsole = [BlueSTSDKFwUpgradeConsole getFwUpgradeConsole: mNode];
            [mConsole readFwVersion:self];
            break;
        case BlueSTSDKNodeStateUnreachable:
            NSLog(@"BlueSTSDKNodeStateUnreachable");
            break;
        case BlueSTSDKNodeStateDisconnecting:
            NSLog(@"BlueSTSDKNodeStateDisconnecting");
            break;
        case BlueSTSDKNodeStateLost:
            NSLog(@"BlueSTSDKNodeStateLost");
            break;
        default:
            NSLog(@"State not handled");
    }
}

#pragma mark - BlueSTSDKManagerDelegate

- (void)manager:(BlueSTSDKManager *)manager didDiscoverNode:(BlueSTSDKNode *)node {
    [self.commandDelegate runInBackground:^{
        // If it's the device we want, let's connect to it.
        if ([node.tag isEqualToString: mCurrentDeviceUUID]) {
            mNode = node;
            [node addNodeStatusDelegate: self];
            [node connect];
        }
    }];
}

- (void)manager:(BlueSTSDKManager *)manager didChangeDiscovery:(BOOL)enable {
    
}

@end

