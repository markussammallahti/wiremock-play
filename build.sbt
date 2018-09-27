name := "wiremock-play"

organization := "mrks"

version := "0.1"

scalaVersion := "2.12.6"

licenses += ("Apache-2.0", url("https://github.com/markussammallahti/scala-utils/blob/master/LICENSE"))

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.6.11" % Provided,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Provided,
  "com.github.tomakehurst" % "wiremock-standalone" % "2.17.0",
  "ch.qos.logback" % "logback-classic" % "1.0.13" % Test
)
