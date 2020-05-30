# AndResGuard

[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[ ![Download](https://api.bintray.com/packages/wemobiledev/maven/com.tencent.mm%3AAndResGuard-core/images/download.svg) ](https://bintray.com/wemobiledev/maven/com.tencent.mm%3AAndResGuard-core/_latestVersion)
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-AndResGuard-green.svg?style=true)](https://android-arsenal.com/details/1/3034)

*Read this in other languages: [English](README.md), [简体中文](README.zh-cn.md).*

`AndResGuard` is a tooling for reducing your apk size, it works like the `ProGuard` for Java source code, but only aim at the resource files. It changes `res/drawable/wechat` to `r/d/a`, and renames the resource file `wechat.png` to `a.png`. Finally, it repackages the apk with 7zip, which can reduce the package size obviously.

`AndResGuard` is fast, and it does **NOT** need the source codes. Input an Android apk, then we can get a 'ResGuard' apk in a few seconds.

Some uses of `AndResGuard` are:

1. Obfuscate android resources. It contains all the resource type(such as drawable、layout、string...). It can prevent your apk from being reversed by `Apktool`.

2. Shrinking the apk size. It can reduce the `resources.arsc` and the package size obviously.

3. Repackage with `7zip`. It supports repackage apk with `7zip`, and we can specify the compression method for each file.

`AndResGuard` is a command-line tool, it supports Windows, Linux and Mac. We suggest you to use 7zip in Linux or Mac platform for a higher compression ratio.

## How to use
### With Gradle
This has been released on `Bintray`
```gradle
apply plugin: 'AndResGuard'

buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath 'com.tencent.mm:AndResGuard-gradle-plugin:1.2.17'
    }
}

andResGuard {
    // mappingFile = file("./resource_mapping.txt")
    mappingFile = null
    use7zip = true
    useSign = true
    // It will keep the origin path of your resources when it's true
    keepRoot = false
    // If set, name column in arsc those need to proguard will be kept to this value
    fixedResName = "arg"
    // It will merge the duplicated resources, but don't rely on this feature too much.
    // it's always better to remove duplicated resource from repo
    mergeDuplicatedRes = true
    whiteList = [
        // your icon
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
        "R.string.google_crash_reporting_api_key",
        "R.string.project_id",
    ]
    compressFilePattern = [
        "*.png",
        "*.jpg",
        "*.jpeg",
        "*.gif",
    ]
    sevenzip {
        artifact = 'com.tencent.mm:SevenZip:1.2.17'
        //path = "/usr/local/bin/7za"
    }

    /**
    * Optional: if finalApkBackupPath is null, AndResGuard will overwrite final apk
    * to the path which assemble[Task] write to
    **/
    // finalApkBackupPath = "${project.rootDir}/final.apk"

    /**
    * Optional: Specifies the name of the message digest algorithm to user when digesting the entries of JAR file
    * Only works in V1signing, default value is "SHA-1"
    **/
    // digestalg = "SHA-256"
}
```

### Wildcard
The whiteList and compressFilePattern support wildcard include ? * +.

```
?	Zero or one character
*	Zero or more of character
+	One or more of character
```

### WhiteList
You need put all resource which access via `getIdentifier` into whiteList.
**You can find more whitsList configs of third-part SDK in [white_list.md](doc/white_list.md). Welcome PR your configs which is not included in white_list.md**

The whiteList only works on the specsName of resources, it wouldn't keep the path of resource.
If you wanna keeping the path, please use `mappingFile` to implement it.

For example, we wanna keeping the path of icon, we need add below into our `mappingFile`.
```
res path mapping:
    res/mipmap-hdpi-v4 -> res/mipmap-hdpi-v4
    res/mipmap-mdpi-v4 -> res/mipmap-mdpi-v4
    res/mipmap-xhdpi-v4 -> res/mipmap-xhdpi-v4
    res/mipmap-xxhdpi-v4 -> res/mipmap-xxhdpi-v4
    res/mipmap-xxxhdpi-v4 -> res/mipmap-xxxhdpi-v4
```

### How to Launch
If you are using `Android Studio`, you can find the generate task option in ```andresguard``` group.
Or alternatively, you run ```./gradlew resguard[BuildType | Flavor]``` in your terminal. The format of task name is as same as `assemble`.

### Sevenzip
The `sevenzip` in gradle file can be set by `path` or `artifact`. Multiple assignments are allowed, but the winner is **always** `path`.

### Result
If finalApkBackupPath is null, AndResGuard will overwrite final APK to the path which assemble[Task] write. Otherwise, it will store in the path you assigned.

### Other
[Looking for more detail](doc/how_to_work.md)


## Known Issue
1. The first element of list which returned by `AssetManager#list(String path)` is empty string when you're using the APK which is compressed by 7zip. [#162](https://github.com/shwenzhang/AndResGuard/issues/162)

## Best Practise
1. Do **NOT** add `resources.arsc` into `compressFilePattern` unless the app size is really matter to you.([#84](https://github.com/shwenzhang/AndResGuard/issues/84) [#233](https://github.com/shwenzhang/AndResGuard/issues/233))
2. Do **NOT** enable 7zip compression(`use7zip`) when you distribute your APP on Google Play. It'll prevent the file-by-file patch when updating your APP. ([#233](https://github.com/shwenzhang/AndResGuard/issues/233))


## Thanks
[Apktool](https://github.com/iBotPeaches/Apktool) Connor Tumbleson

[v2sig](https://github.com/shwenzhang/AndResGuard/pull/133) @jonyChina162
