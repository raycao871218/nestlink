// SPDX-License-Identifier: GPL-3.0-or-later
#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
@interface ZTEngine : NSObject
@property(atomic,assign) BOOL diagnosticsEnabled;
@property(nonatomic,copy,nullable) void (^configurationHandler)(NSString *,NSInteger,NSInteger,NSInteger);
@property(nonatomic,copy,nullable) void (^packetHandler)(NSData *);
@property(nonatomic,copy,nullable) void (^failureHandler)(NSString *);
- (BOOL)startWithNetwork:(NSString *)network gateway:(NSString *)gateway
                 moons:(NSArray<NSDictionary<NSString *,NSString *> *> *)moons
                 store:(NSURL *)store planet:(NSURL *)planet error:(NSError **)error;
- (void)processPackets:(NSArray<NSData *> *)packets completion:(void (^)(void))completion;
- (void)snapshot:(void (^)(NSDictionary<NSString *,id> *))completion;
- (void)resetPath;
- (void)stopWithCompletion:(void (^)(void))completion;
@end
NS_ASSUME_NONNULL_END
