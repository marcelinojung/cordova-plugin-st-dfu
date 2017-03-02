//
//  STDFUPlugin.h
//
//
//  Created by Ronald Garay on 3/1/17.
//
//

#import <Cordova/CDVPlugin.h>

@interface STDFUPlugin : CDVPlugin {
    
}

- (void)checkUpdate:(CDVInvokedUrlCommand *)command;
- (void)initiateUpdate:(CDVInvokedUrlCommand *)command;

@end
