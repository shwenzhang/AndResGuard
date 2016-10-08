# AndResGuard

[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[![Jcenter Status](https://api.bintray.com/packages/simsun/maven/AndResGuard-gradle-plugin/images/download.svg)](https://bintray.com/simsun/maven/AndResGuard-gradle-plugin)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AndResGuard-green.svg?style=true)](https://android-arsenal.com/details/1/3034)

*Read this in other languages: [English](README.md), [简体中文](README.zh-cn.md).*

AndResGuard is a tooling for reducing your apk size, it works like ProGuard for Java source code, but only aim at resource files. It change res/drawable/wechat to r/d/a, and rename the resource file wechat.png to a.png. Finally, it repackages the apk with 7zip, which can reduce the package size obviously.

AndResGuard is fast, and it does not need the source codes. Input a Android apk, then we can get a 'ResGuard' apk in a few seconds.

Some uses of AndResGuard are:

1. Obfuscate android resources, it contain all the resource type(such as drawable、layout、string...). It can prevent your apk reversed by Apktool.

2. Shrinking the apk size, it can reduce the resources.arsc and the package size obviously.

3. Repackage with 7zip, it support repackage apk with 7zip, and we can specify the compression method for each file.

AndResGuard is a command-line tool, it supports Window、Linux and Mac. We suggest you to use 7zip in Linux or Mac platform for a higher compression ratio.

## How to use
### With Gradle
This has been released on Bintray
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
    mappingFile = null
    use7zip = true
    useSign = true
    keepRoot = false
    whiteList = [
        // your icon
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
        artifact = 'com.tencent.mm:SevenZip:1.1.11'
        //path = "/usr/local/bin/7za"
    }
}
```

Run `andresguard/resguard` task to generate compressed APK. This can be done in two ways:
 
If you are using AndroidStudio, you can find the generate task option in ```andresguard``` group.
Or alternatively, you run ```./gradlew resguard``` in your terminal.

The sevenzip can be set by 'path' or 'artifact'. Mutiple assignments are allowed, but the winner always is `path`.

The output apk will be stored in `{App}/build/output/apk/AndResGuard_{apk_name}/{apk_name}_signed_7zip_aligned.apk`
   
Looking for [more detail](doc/how_to_work.md)