set jdkpath=C:\Program Files\Java\jdk-17\bin\java.exe
set storepath=release.keystore
set storepass=testres
set keypass=testres
set alias=testres
set zipalign=C:\Users\zhou0\AppData\Local\Android\Sdk\build-tools\37.0.0\zipalign.exe
"%jdkpath%" -jar AndResGuard-cli-1.2.15.jar input.apk -config config.xml -out outapk -signature "%storepath%" "%storepass%" "%keypass%" "%alias%" -zipalign "%zipalign%"
pause
