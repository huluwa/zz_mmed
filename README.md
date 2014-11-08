mmed
====

An Android app to fetch beauties from image.baidu.com. You can download the app from [XAIOMI APP Store][1] or fork it and build your own version.

This app provide demo for android app beginners who interesting following topic:

1. Picture wall
2. Memory optimize, how to avoid OOM on picture wall
3. Images cached in memory and storage
4. Integrate umeng analyze sdk
5. Integrate waps ad sdk
6. Build multi-version for different market store by ant

setup to build in eclipse
=========================

1. setup [ViewPagerExtensions][2] in eclipse
2. setup [Android-Support-V7-appcompat][3] in eclipse

setup to build with ant in windows
==================================

## config keystore and market

Edit `ant.properties` to config your keystore

    :::text
    key.store=path\\to\\your\\keystore\\file
    key.store.password=YourKeystorePassword
    key.alias=YourKeystoreAlias
    key.alias.password=YourKeystorePassword
    
    #channel name list
    market_channels=xiaomi_waps,tencent_waps,default
    #version code
    version=1.3.0
   
## init ant build
   
    :::shell
    call android update lib-project -p D:/develop/external_libs/ViewPagerExtensions
    call android update lib-project -p D:/tools/android-sdk-windows/extras/android/compatibility/v7/appcompat
    call android update project --name mmed -p D:/kamidox/work/mmed

The path like `D:/kamidox/work/mmed` need to replace path in your environment. Also note that the above command only need to execute on the first time you try to build with ant. After setup, the only thing need to do is invoke following command to build:

## build with ant

    :::shell
    cd D:/kamidox/work/mmed
    ant deploy

`deploy` target will build more than one apk for different market(you know china, we need different app for different market), please refer `custom_rules.xml` for more information.


每美汇
======

一个查看image.baudi.com上美女图片的安卓软件。可以从[小米应用商店][1]下载APK，也可以下载编译你自己的版本。

这个应用实现了如下功能，可作为安卓开发入门用户参考下面的技术内容：

1. 图片墙的实现
2. 内存优化，怎么样避免OOM
3. 图片双缓存，缓存在内存也缓存在内部存储
4. 集成了友盟统计SDK
5. 集成了万普广告SDK
6. 通过ant实现多应用商店自动编译

在Eclipse里编译项目
===================

1. 在Eclipse里配置[ViewPagerExtensions][2]。
2. 在Eclipse里配置[Android-Support-V7-appcompat][3]。

在windows下使用ant来编译
========================

## 配置应用签名证书和要编译的对应市场版本

编辑`ant.properties`文件来配置证书和对应市场版本的APK

    :::text
    key.store=path\\to\\your\\keystore\\file
    key.store.password=YourKeystorePassword
    key.alias=YourKeystoreAlias
    key.alias.password=YourKeystorePassword
    
    #channel name list
    market_channels=xiaomi_waps,tencent_waps,default
    #version code
    version=1.3.0
   
## 初始化ant编译
   
    :::shell
    call android update lib-project -p D:/develop/external_libs/ViewPagerExtensions
    call android update lib-project -p D:/tools/android-sdk-windows/extras/android/compatibility/v7/appcompat
    call android update project --name mmed -p D:/kamidox/work/mmed

`D:/kamidox/work/mmed`等是我本机的目录，你需要根据你自己的环境去修改. 上述命令只需要第一次配置时执行，以后通过ant编译就不需要执行了。

## 使用ant编译

    :::shell
    cd D:/kamidox/work/mmed
    ant deploy

`deploy`是用来编译不同应用市场需要的RELEASE版本的APK。详细可查阅`custom_rules.xml`。

[1]: http://app.mi.com/detail/64165
[2]: https://github.com/astuetz/ViewPagerExtensions
[3]: https://developer.android.com/tools/support-library/setup.html