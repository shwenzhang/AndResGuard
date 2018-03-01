#!/usr/bin/env bash

java -jar AndResGuard-cli-1.2.12-DEBUG.jar input.apk -config config.xml -out outapk -signatureType v2 -signature release.keystore testres testres testres
