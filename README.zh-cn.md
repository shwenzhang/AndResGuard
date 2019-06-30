#  Android资源混淆工具使用说明 #

[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[ ![Download](https://api.bintray.com/packages/wemobiledev/maven/com.tencent.mm%3AAndResGuard-core/images/download.svg) ](https://bintray.com/wemobiledev/maven/com.tencent.mm%3AAndResGuard-core/_latestVersion)
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
        google()
    }
    dependencies {
        classpath 'com.tencent.mm:AndResGuard-gradle-plugin:1.2.16'
    }
}


andResGuard {
    // mappingFile = file("./resource_mapping.txt")
    mappingFile = null
    use7zip = true
    useSign = true
    // 打开这个开关，会keep住所有资源的原始路径，只混淆资源的名字
    keepRoot = false
    // 设置这个值，会把arsc name列混淆成相同的名字，减少string常量池的大小
    fixedResName = "tencent"
    // 打开这个开关会合并所有哈希值相同的资源，但请不要过度依赖这个功能去除去冗余资源
    mergeDuplicatedRes = true
    whiteList = [
        // for your icon
        "R.drawable.icon",
        // for fabric
        "R.string.com.crashlytics.*",
        // for google-services
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
    ]
    sevenzip {
         artifact = 'com.tencent.mm:SevenZip:1.2.16'
         //path = "/usr/local/bin/7za"
    }

    /**
    * 可选： 如果不设置则会默认覆盖assemble输出的apk
    **/
    // finalApkBackupPath = "${project.rootDir}/final.apk"

    /**
    * 可选: 指定v1签名时生成jar文件的摘要算法
    * 默认值为“SHA-1”
    **/
    // digestalg = "SHA-256"
}
```

### 文件通配符
 compressFilePattern和compressFilePattern中的通配符支持? + *

```
?	Zero or one character
*	Zero or more of character
+	One or more of character
```

### 白名单
所有使用`getIdentifier`访问的资源都需要加入白名单。

**请使用Umeng_social_sdk的同学特别留意将资源加入白名单，否则会出现Crash。可以在[white_list.md](doc/white_list.md)查看更多sdk的白名单配置，也欢迎大家PR自己的白名单**

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

### 如何启动
使用Android Studio的同学可以再 `andresguard` 下找到相关的构建任务;
命令行可直接运行```./gradlew resguard[BuildType | Flavor]```， 这里的任务命令规则和assemble一致。

### 配置7Zip
在设置`sevenzip`时, 你只需设置`artifact`或`path`. 支持同时设置,总以path的值为优先.

### 结果
如果没有配置`finalApkBackupPath`，最终结果会覆盖`assemble[BuildType | Flavor]`的输出APK。如果配置则输出至`finalApkBackupPath`配置路径。

### 其他
[点击查看更多细节和命令行使用方法](doc/how_to_work.zh-cn.md)

## 已知问题

1. 当时在使用7zip压缩的APK时，调用`AssetManager#list(String path)`返回结果的首个元素为空字符串. [#162](https://github.com/shwenzhang/AndResGuard/issues/162)

## 最佳实践

1. 如果不是对APK size有极致的需求，请不要把`resources.arsc`添加进`compressFilePattern`. ([#84](https://github.com/shwenzhang/AndResGuard/issues/84) [#233](https://github.com/shwenzhang/AndResGuard/issues/233))
2. 对于发布于Google Play的APP，建议不要使用7Zip压缩，因为这个会导致Google Play的优化Patch算法失效. ([#233](https://github.com/shwenzhang/AndResGuard/issues/233))


## 致谢

[Apktool](https://github.com/iBotPeaches/Apktool) 使用了Apktool资源解码部分的代码

[v2sig](https://github.com/shwenzhang/AndResGuard/pull/133) @jonyChina162
