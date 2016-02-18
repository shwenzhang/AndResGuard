#  Android资源混淆工具使用说明 #

[![Join the chat at https://gitter.im/shwenzhang/AndResGuard](https://badges.gitter.im/shwenzhang/AndResGuard.svg)](https://gitter.im/shwenzhang/AndResGuard?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/shwenzhang/AndResGuard.svg?branch=master)](https://travis-ci.org/shwenzhang/AndResGuard)
[![Jcenter Status](https://api.bintray.com/packages/simsun/maven/AndResGuard-gradle-plugin/images/download.svg)](https://bintray.com/simsun/maven/AndResGuard-gradle-plugin)
[![license](http://img.shields.io/badge/license-apache_2.0-red.svg?style=flat)](https://raw.githubusercontent.com/shwenzhang/AndResGuard/master/LICENSE)
[![Commitizen friendly](https://img.shields.io/badge/commitizen-friendly-brightgreen.svg)](http://commitizen.github.io/cz-cli/)

*其他语言版本: [English](README.md), [简体中文](README.zh-cn.md).*

本文主要是讲述资源混淆组件的用法以及性能，资源混淆组件不涉及编译过程，只需输入一个apk(无论签名与否，debug版，release版均可，在处理过程中会直接将原签名删除)，可得到一个实现资源混淆后的apk(若在配置文件中输入签名信息，可自动重签名并对齐，得到可直接发布的apk)以及对应资源ID的mapping文件。同时可在配置文件中指定白名单，压缩文件(支持*，？通配符)，支持自动签名，保持旧mapping，7z重打包，对齐等功能。   本工具支持Linux、Window跨平台使用，但测试表示若使用7z压缩，Linux下的压缩率更高。

原理介绍：[详见WeMobileDev公众号文章](http://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=208135658&idx=1&sn=ac9bd6b4927e9e82f9fa14e396183a8f#rd)

## 使用Gradle
此工具已发布在Bintray
```
apply plugin: 'AndResGuard'

buildscript {
    dependencies {
        classpath 'com.tencent.mm:AndResGuard-gradle-plugin:1.1.2'
    }
}

andResGuard {
    mappingFile = null
    use7zip = false
    keepRoot = false
    // add <yourpackagename>.R.drawable.icon into whitelist.
    // because the launcher will get thgge icon with his name
    whiteList = [
           "<your_package_name>.R.drawable.icon"
           "<your_package_name>.R.string.com.crashlytics.*"
    ]
    compressFilePattern = [
            "*.png",
            "*.jpg",
            "*.jpeg",
            "*.gif",
            "resources.arsc"
    ]
    zipAlignPath = 'your_zipalign_path'
    sevenZipPath = 'your_7zip_path'
}
```

运行`andresguard/generate`的gradle任务，可以得到资源混淆的安装包

**注意: 请把andResGuard的相关配置放在签名信息的下面,我们会使用你的签名信息用户重新打包APK**

## 如何使用资源混淆工具 ##

### 使用命令行###

我们先看看它的help描述，最简单的使用方式是：java -jar andresguard.jar input.apk，此时会读取运行路径中的config.xml文件，并将结果输出到运行路径中的input(输入apk的名称)中。当然你也可以自己定义：

-config,        指定具体config文件的路径；

-out,           指定具体的输出路径；混淆的mapping会在输出文件夹中以resource_mapping_input(输入apk的名称).txt命名。

-signature,     指定签名信息，若在命令行设置会覆盖config.xml中的签名信息，顺序为签名文件路径、storepass、keypass、storealias。

-mapping,       指定旧的mapping文件，保证同一资源文件在不同版本混淆后的名称保持一致。若在命令行设置会覆盖config.xml中的信息。

-7zip,          指定7zip的路径，若已添加到环境变量不需要设置。应是全路径例如linux: /shwenzhang/tool/7za, Window需要加上.exe      结尾。

> window：
> 对于window应下载命名行版本，若将7za指定到环境变量，即无须设置。地址：[http://sparanoid.com/lab/7z/download.html](http://sparanoid.com/lab/7z/download.html)
>
> linux：sudo apt-get install p7zip-full
>
> mac:sudo brew install p7zip

-zipalign,      指定zipalign的路径，若已添加到环境变量不需要设置。应是全路径例如linux: /shwenzhang/sdk/tools/zipalign, Window需要加上.exe结尾。

-repackage,     如果想要出渠道包等需求，我们可能希望利用7zip直接重打包安装包。

![](http://i.imgur.com/FssA59a.jpg)

**2.简单用法**

	java -jar andresguard.jar input.apk

若想指定配置文件或输出目录：

	java -jar andresguard.jar input.apk -config yourconfig.xml -out output_directory

若想指定签名信息或mapping信息：

	java -jar andresguard.jar input.apk -config yourconfig.xml
		-out output_directory -signature signature_file_path storepass_value
		keypass_value storealias_value -mapping mapping_file_path

若想指定7zip或zipalign的路径(若已设置环境变量，这两项不需要单独设置)：

	java -jar andresguard.jar input.apk
	 -7zip /shwenzhang/tool/7za  -zipalign /shwenzhang/sdk/tools/zipalign

若想用7zip重打包安装包，同时也可指定output路径，指定7zip或zipalign的路径(此模式其他参数都不支持)：

	java -jar andresguard.jar -repackage input.apk -out output_directory
	 -7zip /shwenzhang/tool/7za  -zipalign /shwenzhang/sdk/tools/zipalign   


###使用资源混淆工具会得到什么 ##

正常来说，我们可得到以下output路径得到以下7个有用的文件：(需要把zipalign也加入环境变量)

![](http://i.imgur.com/UDtxKqO.png)

混淆过程中会输出log,主要是可看到耗费时间，以及相对输入apk减少的大小。

![](http://i.imgur.com/ICDkJCH.png)

##  如何写配置文件 ##

配置文件中主要有五大项，即property，whitelist, keepmapping, compress,sign。


**1. Property项**

Property主要设置一些通用属性：

--sevenzip, 是否使用7z重新压缩签名后的apk包(这步一定要放在签名后，不然签名时会破坏效果)，需要我们安装7z命令行，同时加入环境变量中，同时要求输入签名信息(不然不会使用)。

> Window：7z command line version, 即7za(http://www.7-zip.org/download.html)
>  
> Linux:  可直接sudo apt-get install p7zip-full。
>  
> 注意：效果很好，推荐使用，并且在Linux(Mac的高富帅也可)上。

--metaname, 由于重打包时需要删除签名信息，考虑到这个文件名可能会被改变，所以使用者可手动输入签名信息对应的文件名。默认为META_INF。

--keeproot, 是否将res/drawable混淆成r/s

![](http://i.imgur.com/JfkZ09e.gif)

**2. Whitelist项**

Whitelist主要是用来设置白名单，由于我们代码中某些资源会通过getIdentifier(需要全局搜索所有用法并添加到白名单)或动态加载等方式，我们并不希望混淆这部分的资源ID：

--isactive, 是否打开白名单功能；

--path,     是白名单的项，格式为package_name.R.type.specname,由于一个resources.arsc中可能会有多个包，所以这里要求写全包名。同时支持*，？通配符，例如: com.tencent.mm.R.drawable.emoji_*、com.tencent.mm.R.drawable.emoji_？；    

注意:1.不能写成com.tencent.mm.R.drawable.emoji.png，即带文件后缀名；2. *通配符代表.+,即a*,不能匹配到a；

![](http://i.imgur.com/VZ4fOa2.gif)

**3. Keepmapping项**

Keepmapping主要用来指定旧的mapping文件，为了保持一致性，我们支持输入旧的mapping文件，可保证同一资源文件在不同版本混淆后的名称保持一致。另一方面由于我们需要支持增量下载方式，如果每次改动都导致所有文件名都会更改，这会导致增量文件增大，但测试证明影响并不大(后面有测试数据)。

--isactive, 是否打开keepmapping模式；

--path,     是旧mapping文件的位置，linux用/, window用 \;

![](http://i.imgur.com/y2LZRe9.gif)

**4. Compress项**

Compress主要用来指定文件重打包时是否压缩指定文件，默认我们重打包时是保持输入apk每个文件的压缩方式(即Stored或者Deflate)。一般来说，1、在2.3版本以下源文件大于1M不能压缩；2、流媒体不能压缩。对于.png、.jpg是可以压缩的，只是AssetManger读取时候的方式不同。

--isactive, 是否打开compress模式；

--path,     是需要被压缩文件的相对路径(相对于apk最顶层的位置)，这里明确一定要使用‘/’作为分隔符，同时支持通配符*，？，例如*.png(压缩所有.png文件)，res/drawable/emjio_?.png，resouces.arsc(压缩    resources.arsc)

注意若想得到最大混淆：

1. 输入四项个path:*.png, *.jpg, *.jpeg, *.gif

2. 若你的resources.arsc原文件小于1M，可加入resourcs.arsc这一项！若不需要支持低版本，直接加入也可。

![](http://i.imgur.com/9lTPiPA.gif)


**5. Sign项**

Sign主要是对处理后的文件重签名，需要我们输入签名文件位置，密码等信息。若想使用7z功能就一定要填入相关信息。

--isactive,  是否打开签名功能；

--path,      是签名文件的位置，linux用/, window用 \;

--storepass, 是storepass的数值;

--keypass,   是keypass的数值;

--alias,     是alias的数值；

![](http://i.imgur.com/21yO1jY.gif)

 注意： 若出于保密不想写在config.xml，可用-signature命令行设置模式。config.xml中的签名信息会被命令行覆盖。

## Android资源混淆工具需要注意的问题 ##

1. compress参数对混淆效果的影响
若指定compess 参数.png、.gif以及*.jpg，resources.arsc会大大减少安装包体积。若要支持2.2，resources.arsc需保证压缩前小于1M。

2. 操作系统对7z的影响
实验证明，linux与mac的7z效果更好

3. keepmapping方式对增量包大小的影响
影响并不大，但使用keepmapping方式有利于保持所有版本混淆的一致性

4. 渠道包的问题(**建议通过修改zip摘要的方式生产渠道包**)
在出渠道包的时候，解压重压缩会破坏7zip的效果，通过repackage命令可用7zip重压缩。

5. 若想通过getIdentifier方式获得资源，需要放置白名单中。
部分手机桌面快捷图标的实现有问题，务必将程序桌面icon加入白名单。

6. 对于一些第三方sdk,例如友盟，可能需要将部分资源添加到白名单中。

```xml
    <issue id="whitelist" isactive="true">
        <path value ="yourpackagename.R.string.umeng*" />   
        <path value ="yourpackagename.R.layout.umeng*" />
        <path value ="yourpackagename.R.drawable.umeng*" />
        <path value ="yourpackagename.R.anim.umeng*" />
        <path value ="yourpackagename.R.color.umeng*" />
        <path value ="yourpackagename.R.style.*UM*" />
        <path value ="yourpackagename.R.style.umeng*" />
        <path value ="yourpackagename.R.id.umeng*" />
        <path value ="yourpackagename.R.string.com.crashlytics.*" />
    </issue>
```

##  Androd资源混淆工具的耗时与效果 ##

**1. 基本的耗时与效果**

以微信的5.4为例，使用组件中的resoureproguard.jar进行资源混淆，具体的性能数据如下：

其中时间指的是从最开始到该步骤完成的时间，而不是每步骤独立时间。

![](http://i.imgur.com/8i62qbJ.jpg)

**2. compres参数(下文有详细描述，是否压缩某些资源)对安装包大小的影响**

若指定compess 参数*.png、*.gif以及*.jpg，resources.arsc对安装包大小影响如下：

![](http://i.imgur.com/4kWgw6o.jpg)

但是resources.arsc如果原文件大于1M，压缩后是不能在系统2.3以下运行的。

**3. 操作系统对7z的影响**

由于7z过程中使用的是极限压缩模式，所以遍历次数会增多(7次)，时间相对会比较长。假设不使用7z在单核的虚拟机中仅需10秒。

同时我们需要注意是由于文件系统不一致，在window上面使用7z生成的安装包会较大，微信在window以及linux下7z的效果如下：

![](http://i.imgur.com/t8SPz4Q.png)

所以最后出包请使用Linux(Mac亦可)，具体原因应该与文件系统有关。

**4. keepmapping方式(下文有详细描述，是否保持旧的mapping)对增量包大小的影响**

我们一般使用bsdiff生成增量包，bsdiff差分的是二进制，利用LCS最长公共序列算法。假设分别使用正序与逆序混淆规则对微信5.4作资源混淆(即它们的混淆方式是完全相反的)。

![](http://i.imgur.com/u7obKDt.png)

事实上，它们的差分是不需要371kb,因为有比较大的文件格式，共同标记部分。

现在我们做另外一个实验，首先对微信5.3.1作资源混淆得到安装包a，然后以keepmapping方式对微信5.4作资源混淆得到安装包b，最后以完全逆序的方式对微信5.4作资源混淆得到安装包c。

分别用安装包b、c对安装包a生成增量文件d,e。比较增量文件d、e的大小，分别如下：

![](http://i.imgur.com/MNY9AHr.png)


所以增量文件的大小并不是我们采用keepmapping方式的主要考虑因素，保持混淆的一致性，便于查找问题或是更加重要的考虑。

**5.安装包缩减的原因与影响因素**

总结，安装包大小减少的原因以下四个：

![](http://i.imgur.com/tkC2xr5.png)

相对的，可得到影响效果的因素有以下几个：

![](http://i.imgur.com/VEG9cP6.png)
