set jdkpath=D:\Program Files\Java\jdk1.7.0_79\bin\java.exe
set storepath=release.keystore
set storepass=testres
set keypass=testres
set alias=testres
set zipalign=D:\soft\dev\android\sdk\build-tools\23.0.2\zipalign.exe
"%jdkpath%" -jar AndResGuard-cli-1.1.10.jar input.apk -config config.xml -out outapk -signature "%storepath%" "%storepass%" "%keypass%" "%alias%" -zipalign "%zipalign%"
pause
