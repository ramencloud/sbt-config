sbtPlugin := true

organization in ThisBuild := "dotdata"
name := "sbt-config"
version in ThisBuild := "0.1.4-SNAPSHOT"

scalaVersion := "2.12.5"

addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"  % "1.16")

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test
