set jdkpath=C:\Program Files\Java\jdk-17\bin\java.exe
set storepath=release.keystore
set storepass=testres
set keypass=testres
set alias=testres
set zipalign=C:\Users\zhou0\AppData\Local\Android\Sdk\build-tools\37.0.0\zipalign.exe
set sevenzip=C:\Users\zhou0\tools\AndResGuard\SevenZip\executable\SevenZip-windows-x86_64.exe
set apksigner=C:\Users\zhou0\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat
"%jdkpath%" -jar AndResGuard-cli-1.2.21.jar input.apk -config config.xml -out outapk -signature "%storepath%" "%storepass%" "%keypass%" "%alias%" -7zip "%sevenzip%" -zipalign "%zipalign%"
"%apksigner%" sign --ks "%storepath%" --ks-key-alias "%alias%" --ks-pass pass:%storepass% --key-pass pass:%keypass% --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false --in outapk\input_signed_7zip_aligned.apk --out outapk\input_signed.apk
pause
