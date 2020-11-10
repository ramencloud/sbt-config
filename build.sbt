sbtPlugin := true

organization in ThisBuild := "dotdata"
name := "sbt-config"
version in ThisBuild := "0.1.4-SNAPSHOT"

scalaVersion := "2.12.5"

addSbtPlugin("com.lucidchart"  %% "sbt-scalafmt"  % "1.16")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.3")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.5.1")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")


// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test
