enablePlugins(JavaAppPackaging)

name := "magda-registry"
organization := "au.com.csiro.data61"
version := "0.0.1"
scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.4.9"
  val scalaTestV  = "2.2.6"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV,
    "ch.megard" %% "akka-http-cors" % "0.1.5",
    "org.scalikejdbc" %% "scalikejdbc" % "2.4.2",
    "org.scalikejdbc" %% "scalikejdbc-config"  % "2.4.2",
    "ch.qos.logback"  %  "logback-classic" % "1.1.7",
    "org.postgresql"  %  "postgresql" % "9.4.1211.jre7",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}

Revolver.settings
