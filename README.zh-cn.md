#  Android资源混淆工具使用说明 #

[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[![Jcenter Status](https://api.bintray.com/packages/simsun/maven/AndResGuard-gradle-plugin/images/download.svg)](https://bintray.com/simsun/maven/AndResGuard-gradle-plugin)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AndResGuard-green.svg?style=true)](https://android-arsenal.com/details/1/3034)


*其他语言版本: [English](README.md), [简体中文](README.zh-cn.md).*

本文主要是讲述资源混淆组件的用法以及性能，资源混淆组件不涉及编译过程，只需输入一个apk(无论签名与否，debug版，release版均可，在处理过程中会直接将原签名删除)，可得到一个实现资源混淆后的apk(若在配置文件中输入签名信息，可自动重签名并对齐，得到可直接发布的apk)以及对应资源ID的mapping文件。同时可在配置文件中指定白名单，压缩文件(支持*，？通配符)，支持自动签名，保持旧mapping，7z重打包，对齐等功能。   本工具支持Linux、Window跨平台使用，但测试表示若使用7z压缩，Linux下的压缩率更高。

原理介绍：[详见WeMobileDev公众号文章](http://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=208135658&idx=1&sn=ac9bd6b4927e9e82f9fa14e396183a8f#rd)

## 使用Gradle
此工具已发布在Bintray
```gradle
apply plugin: 'AndResGuard'

buildscript {
    dependencies {
        classpath 'com.tencent.mm:AndResGuard-gradle-plugin:1.1.10'
    }
}


andResGuard {
    mappingFile = null
    use7zip = true
    useSign = true
    keepRoot = false
    whiteList = [
        //for your icon
        "R.drawable.icon",
        //for fabric
        "R.string.com.crashlytics.*",
        //for umeng update
        "R.string.umeng*",
        "R.string.UM*",
        "R.string.tb_*",
        "R.layout.umeng*",
        "R.layout.tb_*",
        "R.drawable.umeng*",
        "R.drawable.tb_*",
        "R.anim.umeng*",
        "R.color.umeng*",
        "R.color.tb_*",
        "R.style.*UM*",
        "R.style.umeng*",
        "R.id.umeng*",
        //umeng share for sina
        "R.drawable.sina*"
        // for google-services.json
        "R.string.google_app_id",
        "R.string.gcm_defaultSenderId",
        "R.string.default_web_client_id",
        "R.string.ga_trackingId",
        "R.string.firebase_database_url",
        "R.string.google_api_key",
        "R.string.google_crash_reporting_api_key"
    ]
    compressFilePattern = [
        "*.png",
        "*.jpg",
        "*.jpeg",
        "*.gif",
        "resources.arsc"
    ]
     sevenzip {
         artifact = 'com.tencent.mm:SevenZip:1.1.10'
         //path = "/usr/local/bin/7za"
    }
}
```

运行`andresguard/resguard`的gradle任务，可以得到资源混淆的安装包
命令行可直接运行```./gradlew resguard```

在设置`sevenzip`时, 你只需设置`artifact`或`path`. 支持同时设置,总以path的值为优先.

点击查看[更多细节和命令行使用方法](doc/how_to_work.zh-cn.md)