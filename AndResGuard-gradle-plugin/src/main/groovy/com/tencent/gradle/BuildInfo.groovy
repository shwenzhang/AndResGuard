package com.tencent.gradle

/**
 * Created by simsun on 5/13/16.*/

class BuildInfo {
  def file
  def signConfig
  def packageName
  def buildType
  def flavors
  def taskName
  def minSDKVersion

  BuildInfo(file, sign, packageName, buildType, flavors, taskName, minSDKVersion) {
    this.file = file
    this.signConfig = sign
    this.packageName = packageName
    this.buildType = buildType
    this.flavors = flavors
    this.taskName = taskName
    this.minSDKVersion = minSDKVersion
  }

  @Override
  String toString() {
    """| file = ${file}
       | packageName = ${packageName}
       | buildType = ${buildType}
       | flavors = ${flavors}
       | taskname = ${taskName}
       | minSDKVersion = ${minSDKVersion}
    """.stripMargin()
  }
}
