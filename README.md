#  AndResGuard #

*Read this in other languages: [English](README.md), [简体中文](README.zh-cn.md).*

AndResGuard is a tool to proguard resource for Android, just like ProGuard in Java. It can change res/drawable/wechat to r/d/a, and rename the resource file wechat.png to a.png. Finally, it repackages the apk with 7zip, which can reduce the package size obviously.

AndResGuard is fast, and it does not need the source codes. Input a Android apk, then we can get a 'ResGuard' apk in a few seconds.

Some uses of AndResGuard are:

1. Obfuscate android resources, it contain all the resource type(such as drawable、layout、string...). It can prevent your apk reversed by Apktool.

2. Shrinking the apk size, it can reduce the resources.arsc and the package size obviously.

3. Repackage with 7zip, it support repackage apk with 7zip, and we can specify the compression method for each file.

AndResGuard is a command-line tool, it supports Window、Linux and Mac. We suggest you to use 7zip in Linux or Mac platform for a higher compression ratio.

## How to use ##

**1.how to use**
```
    java -jar andresguard.jar -h
```
we can see the help description, The easiest way is : `java -jar andresguard.jar input.apk`. Then it would try to read the config,xml, and output the results to the directory with the name of input.apk.

- -config,        set the config file yourself, if not, the default path is the running location with name config.xml.

- -out,           set the output directory yourself, if not, the default directory is the running location with name of the input file

- -signature,    set sign property, following by parameters: signature_file_path storepass keypass storealias, if you set these, the sign data in the config file will be overlayed.

- -mapping,       set keep mapping property, following by parameters: mapping_file_path. if you set these, the mapping data in the config file will be overlayed.

- -7zip,          set the 7zip path, such as /home/shwenzhang/tools/7za, window will be end of 7za.exe.

> Window：
> set 7za to environment variables。Address：[http://sparanoid.com/lab/7z/download.html](http://sparanoid.com/lab/7z/download.html)
>
> linux：sudo apt-get install p7zip-full
>
> mac:sudo brew install p7zip

- -zipalign,      set the zipalign, such as /home/shwenzhang/sdk/tools/zipalign, window will be end of zipalign.exe.

- -repackage,      usually, when we build the channeles apk, it may destroy the 7zip. so you may need to use 7zip to repackage the apk

![](http://i.imgur.com/FssA59a.jpg)

**2.samples**

	java -jar resourceproguard.jar input.apk

if you want to special the output path or config file path, you can input:

	java -jar resourceproguard.jar input.apk -config yourconfig.xml -out output_directory

if you want to special the sign or mapping data, you can input:

	java -jar resourceproguard.jar input.apk -config yourconfig.xml
		-out output_directory -signature signature_file_path storepass_value
		keypass_value storealias_value -mapping mapping_file_path

if you want to special 7za or zipalign path, you can input:

	java -jar resourceproguard.jar input.apk
	 -7zip /shwenzhang/tool/7za  -zipalign /shwenzhang/sdk/tools/zipalign

if you just want to repackage an apk compress with 7z：

	java -jar resourceproguard.jar -repackage input.apk -out output_directory
	 -7zip /shwenzhang/tool/7za  -zipalign /shwenzhang/sdk/tools/zipalign   

## What we get ##

Normally, we can get the following 7 useful files:

![](http://i.imgur.com/LtzSGC4.png)

During the process, we can see the cost time and  the reduce size.

![](http://i.imgur.com/ICDkJCH.png)


##How to write config.xml file ##

There are five main configurations:property, whitelist, keepmapping, compress, sign。

**1. Property**

Common properties：

- --sevenzip, whether use 7zip to repackage the signed apk, you must install the 7z command line version first.

- --metaname, the sign data file name in your apk, default must be META-INF.

- --keeproot, if keep root, res/drawable will be kept, it won't be changed to such as r/s.

![](http://i.imgur.com/JfkZ09e.gif)

**2. Whitelist**

Whitelist property is used for keeping the resource you want. Because some resource id you can not proguard, such as throug method getIdentifier.

- --isactive,  whether to use whitelist, you can set false to close it simply.

- --path,  you must write the full package name, such as com.tencent.mm.R.drawable.icon. For some reason, we should keep our icon better, and it support *, ?, such as com.tencent.mm.R.drawable.emoji_* or com.tencent.mm.R.drawable.emoji_?   

Warning:1. donot write the file format name,  such com.tencent.mm.R.drawable.emoji.png；2. * mean .+, a* would not match a；

![](http://i.imgur.com/VZ4fOa2.gif)

**3. Keepmapping**

sometimes if we want to keep the last way of obfuscation, we can use keepmapping mode. It is just like applymapping in ProGuard.

- --isactive, whether to use keepmapping, you can set false to close it simply.

- --path,     the old mapping file, in window use \, in linux use /, and the default path is the running location.

![](http://i.imgur.com/y2LZRe9.gif)

**4. Compress**

Compress can specify the compression method for each file(Stored or Deflate). Generally, 1. blow 2.3 version, if the source file is larger than 1M, then is can not be compressed; 2, streaming media can not be compressed, such as .wav, .mpg.

- --isactive,  whether to use compress, you can set false to close it simply.

- --path,     you must use / separation, and it support *, ?, such as *.png, *.jpg, res/drawable-hdpi/welcome_?.png.

The maximum confusion will be：

1. paths:*.png, *.jpg, *.jpeg, *.gif

2. resources.arsc

![](http://i.imgur.com/9lTPiPA.gif)


**5. Sign**

if you want to sign the apk, you should input following data, but if you want to use 7zip, you must fill them

- --isactive,   whether to use sign, you can set false to close it simply.

- --path,     the signature file path, in window use \, in linux use /, and the default path is the running location.

- --storepass, storepass value.

- --keypass,   keypass value.

- --alias,     alias value.

![](http://i.imgur.com/21yO1jY.gif)

Warning： if you use -signature mode。these setting in config.xml will be overlayed.

## FQA ##

1. How to use compress flag
If you use compess flag with *.png、*.gif、*.jpg，it will help you decrease the size of file `resources.arsc`
NOTE: If your app support Android2.2 and below, the size of file `resources.arsc` should be below 1M.

2. keepmapping flag impact on the size of increasing package
keepmapping will help to keep your coherence in different version

3. packages for different channel
Repackage will make 7zip invalid，you should repackage all channel apk.

4. wanna get resource with `getIdentifier`
You should add these resources to whitelist.
NOTE: *You should add your icon to whitelist, because of some launchers' special implementation*

5. Use umeng or other sdk
You should add umeng resource to whitelist
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
		</issue>
```
