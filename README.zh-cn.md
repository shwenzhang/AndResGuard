#  Android资源混淆工具使用说明 #

[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[![Jcenter Status](https://api.bintray.com/packages/simsun/maven/AndResGuard-gradle-plugin/images/download.svg)](https://bintray.com/simsun/maven/AndResGuard-gradle-plugin)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AndResGuard-green.svg?style=true)](https://android-arsenal.com/details/1/3034)


*其他语言版本: [English](README.md), [简体中文](README.zh-cn.md).*

`AndResGuard`是一个帮助你缩小APK大小的工具，他的原理类似Java Proguard，但是只针对资源。他会将原本冗长的资源路径变短，例如将`res/drawable/wechat`变为`r/d/a`。

`AndResGuard`不涉及编译过程，只需输入一个apk(无论签名与否，debug版，release版均可，在处理过程中会直接将原签名删除)，可得到一个实现资源混淆后的apk(若在配置文件中输入签名信息，可自动重签名并对齐，得到可直接发布的apk)以及对应资源ID的mapping文件。

原理介绍：[详见WeMobileDev公众号文章](http://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=208135658&idx=1&sn=ac9bd6b4927e9e82f9fa14e396183a8f#rd)

## 使用Gradle
此工具已发布在Bintray
```gradle
apply plugin: 'AndResGuard'

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.tencent.mm:AndResGuard-gradle-plugin:1.1.11'
    }
}


andResGuard {
    // mappingFile = file("./resource_mapping.txt")
    mappingFile = null
    use7zip = true
    useSign = true
    // 打开这个开关，会keep住所有资源的原始路径，只混淆资源的名字
    keepRoot = false
    whiteList = [
        // for your icon
        "R.drawable.icon",
        // for fabric
        "R.string.com.crashlytics.*",
        // for umeng update
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
        // umeng share for sina
        "R.drawable.sina*",
        // for google-services.json
        "R.string.google_app_id",
        "R.string.gcm_defaultSenderId",
        "R.string.default_web_client_id",
        "R.string.ga_trackingId",
        "R.string.firebase_database_url",
        "R.string.google_api_key",
        "R.string.google_crash_reporting_api_key"
        // umeng share for facebook
        "R.layout.*facebook*",
        "R.id.*facebook*",
        // umeng share for messager
        "R.layout.*messager*",
        "R.id.*messager*",
        // umeng share commond
        "R.id.progress_bar_parent",
        "R.id.webView"
    ]
    compressFilePattern = [
        "*.png",
        "*.jpg",
        "*.jpeg",
        "*.gif",
        "resources.arsc"
    ]
     sevenzip {
         artifact = 'com.tencent.mm:SevenZip:1.1.11'
         //path = "/usr/local/bin/7za"
    }
}
```

运行`andresguard/resguard`的gradle任务，可以得到资源混淆的安装包
命令行可直接运行```./gradlew resguard```

在设置`sevenzip`时, 你只需设置`artifact`或`path`. 支持同时设置,总以path的值为优先.

最终的混淆APK会生成在`{App}/build/output/apk/AndResGuard_{apk_name}/{apk_name}_signed_7zip_aligned.apk`。
    
**请使用Umeng_social_sdk的同学特别留意将资源加入白名单，否则会出现Crash。也欢迎大家PR自己的白名单**
    
白名单机制只作用于资源的specsName，不会keep住资源的路径。如果想keep住资源原有的物理路径，可以使用`mappingFile`。
例如我想keep住icon所有folder，可以在mappingFile指向的文件添加：

```
res path mapping:
    res/mipmap-hdpi-v4 -> res/mipmap-hdpi-v4
    res/mipmap-mdpi-v4 -> res/mipmap-mdpi-v4
    res/mipmap-xhdpi-v4 -> res/mipmap-xhdpi-v4
    res/mipmap-xxhdpi-v4 -> res/mipmap-xxhdpi-v4
    res/mipmap-xxxhdpi-v4 -> res/mipmap-xxxhdpi-v4
```

[点击查看更多细节和命令行使用方法](doc/how_to_work.zh-cn.md)

