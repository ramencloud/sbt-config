sbtPlugin := true

organization in ThisBuild := "com.dotdata"
name := "sbt-config"

val baseVersion = "0.1"

version in ThisBuild := {
  if (sys.env.get("GITHUB_REF").contains("refs/heads/master")) {
    s"$baseVersion.${sys.env("GITHUB_RUN_ID")}"
  } else {
    val githubVersion =
      for {
        runId <- sys.env.get("GITHUB_RUN_ID")
        sha <- sys.env.get("GITHUB_SHA")
      } yield {
        s"$baseVersion.$runId-$sha"
      }

    val localDevVersion = s"$baseVersion.0-SNAPSHOT"

    githubVersion.getOrElse(localDevVersion)
  }
}


scalaVersion := "2.12.5"

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.5.1")

// Make the build faster, since there is no Scaladocs anyway
sources in (Compile,doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false

// the following prevents thousands of meaningless stacktraces by docker plugin on JDK 9
libraryDependencies += "javax.activation" % "activation" % "1.1.1" % Test
