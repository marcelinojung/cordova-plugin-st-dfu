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
        mCurrentDeviceUUID = [command.arguments objectAtIndex:0]; // Get the UUID of device so we can extract version number from name.
        
        dispatch_queue_t centralQueue = dispatch_queue_create("com.vensi.centralManager", DISPATCH_QUEUE_SERIAL);
        CBCentralManager *centralManager = [[CBCentralManager alloc] initWithDelegate:nil queue:centralQueue];
        
        if (centralManager != nil) {
            NSArray *connectedStDevice = [centralManager retrievePeripheralsWithIdentifiers:@[[[NSUUID alloc] initWithUUIDString:mCurrentDeviceUUID]]];
            CBPeripheral *connectedPeripheral = connectedStDevice.count > 0 ? [connectedStDevice objectAtIndex: 0] : nil;
            
            if (connectedPeripheral != nil) {
                NSError *error;
                NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern: @"[0-9][0-9][0-9]" options:0 error:&error];
                
                NSTextCheckingResult *match = [regex firstMatchInString:connectedPeripheral.name
                                                                options:0
                                                                  range:NSMakeRange(0, [connectedPeripheral.name length])];
                if (match != nil) {
                    NSInteger version = [[connectedPeripheral.name substringWithRange:[match rangeAtIndex:0]] integerValue];
                    
                    NSInteger newVersion = [self downloadVersion];
                    if (version <= newVersion) { //TODO: Need to compare with version.
                        dispatch_async(dispatch_get_main_queue(), ^{
                            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"" message:@"New update available. Would you like to update your device?" delegate:self cancelButtonTitle:@"NO" otherButtonTitles:@"YES", nil];
                            alert.delegate = self;
                            [alert show];
                        });
                    }
                }
            }
        }
    }];
}

- (void) initiateUpdate:(CDVInvokedUrlCommand *)command {
    [mSTManager discoveryStart];
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

- (NSInteger) downloadVersion {
    NSInteger version = 0;
    NSData *data = [NSData dataWithContentsOfURL: NSURL *url = [NSURL URLWithString: @"https://s3.amazonaws.com/sttile.blueapp.io/test/version.json"]];
    
    if(data != nil) {
        NSError *error;
        NSDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
        version = [[jsonDict valueForKey:@"version"] integerValue];
    }
    
    return version;
}

#pragma mark - UIAlertViewDelegate

- (void) alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
    if(buttonIndex == YES) {
        mUpdateView.hidden = NO;
        
        [self.commandDelegate runInBackground:^{
            [mUpdateView showOverlay: @"Preparing device for update. Please wait. This may take 5 minutes."];
            
            CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK
                                                        messageAsString: @"disconnect"];
            
            result.keepCallback = [NSNumber numberWithInt:1];
            [self.commandDelegate sendPluginResult: result callbackId: mCurrentCallbackId];
        }];
    }
}

#pragma mark - BlueSTSDKFwUpgradeReadVersionDelegate

- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console didVersionRead:(BlueSTSDKFwVersion *)version {
    NSLog(@"didVersionRead");
    
    if(version == nil){
        NSLog(@"Error reading version");
    }
    
    [mUpdateView showProgressView];
    
    [mConsole loadFwFile:[self downloadFile] delegate:self];
}

#pragma mark - BlueSTSDKFwUpgradeUploadFwDelegate


- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadProgres:(NSURL *)file loadBytes:(NSUInteger)load {
    NSLog(@"onLoadProgerss");
    if (![mUpdateView displayingProgress]) {
        [mUpdateView showProgressView];
    } else {
        float progress = (1.0f - load / (float)mFwFileLength);
        
        [mUpdateView updateProgressView: progress];
    }
}

-(void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadComplite:(NSURL *)file {
    NSLog(@"onLoadComplite");
    
    [mUpdateView removeOverlay];
    mUpdateView.hidden = YES;
}

- (void) fwUpgrade:(BlueSTSDKFwUpgradeConsole *)console onLoadError:(NSURL *)file error:(BlueSTSDKFwUpgradeUploadFwError)error {
    NSLog(@"onLoadError");
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
