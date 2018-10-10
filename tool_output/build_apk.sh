#!/usr/bin/env bash

java -jar AndResGuard-cli-1.2.15.jar input.apk -config config.xml -out outapk -signatureType v2 -signature release.keystore testres testres testres
