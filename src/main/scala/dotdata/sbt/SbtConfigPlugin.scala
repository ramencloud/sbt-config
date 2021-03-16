package dotdata.sbt

import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin.autoImport._
import scoverage.ScoverageKeys._
import sbt.Keys._
import sbt._
import scala.collection.JavaConverters._

object SbtConfigPlugin extends AutoPlugin {

  object autoImport {

    // Compiler settings

    def compilerSettings(versionOfScala: String = "2.12.13"): Def.SettingsDefinition = {
      Seq(
        scalaVersion := versionOfScala,
        fork := true,
        scalacOptions := Seq(
          "-language:higherKinds",
          "-language:implicitConversions",
          "-language:postfixOps",
          "-encoding",
          "utf8"
        )
      )
    }

    // Formatting

    val generateScalafmtConfTask = Def.task {
      val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
      val formatConfFile     = resourceManaged.value / "scalafmt.conf"
      IO.delete(formatConfFile)
      IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
      Seq(formatConfFile)
    }

    val generateScalastyleConfTask = Def.task {
      val stream    = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
      val styleFile = resourceManaged.value / "scalastyle-config.xml"
      IO.delete(styleFile)
      IO.write(styleFile, IO.readBytes(stream))
      Seq(styleFile)
    }

    lazy val formatSettings: Def.SettingsDefinition = {
      Seq(
        Compile / resourceGenerators += generateScalafmtConfTask.taskValue,
        scalafmtConfig := {
          val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
          val formatConfFile     = resourceManaged.value / "scalafmt.conf"
          IO.delete(formatConfFile)
          IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
          formatConfFile
        },

        // Configuration below for formatting "main" and "test" folders on `sbt test`
        Test / test / testExecution := {
          (Compile / scalafmt).value
          (Test / scalafmt).value
          (Test / test / testExecution).value
        }
      ) ++
      Seq(Compile, Test).flatMap(inConfig(_) { Seq(
        scalafmtCheckAll := scalafmtCheckAll.dependsOn(generateScalafmtConfTask).value,
        scalafmtCheck := scalafmtCheck.dependsOn(generateScalafmtConfTask).value,
        scalafmtSbt := scalafmtSbt.dependsOn(generateScalafmtConfTask).value,
        scalafmtSbtCheck := scalafmtSbtCheck.dependsOn(generateScalafmtConfTask).value,
      )})
    }

    // Linting

    def scalastyleSettings(excludes: String = ""): Def.SettingsDefinition = {

      if (excludes.nonEmpty) {
        Seq(
          (Compile / scalastyleSources) := {
            ((Compile / scalaSource).value ** "*.scala").get.filterNot(_.getAbsolutePath.contains(excludes))
          },
          (Test / scalastyleSources) := {
            ((Test / scalaSource).value ** "*.scala").get.filterNot(_.getAbsolutePath.contains(excludes))
          }
        )
      } else {
        Seq()
      } ++ Seq(
        Compile / resourceGenerators += generateScalastyleConfTask.taskValue,

        (scalastyle in Compile) := (scalastyle in Compile) dependsOn generateScalastyleConfTask,
        (scalastyle in Test) := (scalastyle in Test) dependsOn generateScalastyleConfTask,

        scalastyleConfig := {
          val stream    = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
          val styleFile = resourceManaged.value / "scalastyle-config.xml"
          IO.delete(styleFile)
          IO.write(styleFile, IO.readBytes(stream))
          styleFile
        },

        clean := Def.sequential(
          clean,
          generateScalastyleConfTask,
          generateScalafmtConfTask,
        ),

        // Configuration below for formatting "main" and "test" folders on `sbt test`
        Test / test / testExecution := {
          (Compile / scalastyle).toTask("").value
          (Test / scalastyle).toTask("").value
          (Test / test / testExecution).value
        }
      ) ++ Seq(Compile, Test).flatMap(inConfig(_) { Seq(
        scalastyle := scalastyle.dependsOn(generateScalastyleConfTask).inputTaskValue,
      )})
    }

    // Deprecations are not immediate and need a notice
    val fatalWarningsExceptDeprecation: Def.SettingsDefinition =
      Seq(Compile, Test).flatMap(inConfig(_) {
        compile := {
          val compiled = compile.value
          val problems = compiled.readSourceInfos().getAllSourceInfos.asScala.flatMap {
            case (_, info) =>
              info.getReportedProblems
          }

          val deprecationsOnly = problems.forall { problem =>
            problem.message().contains("is deprecated")
          }

          if (!deprecationsOnly) sys.error("Fatal warnings: some warnings other than deprecations were found.")
          compiled
        }
      })

    val scalacLintingSettings: Seq[String] = Seq(
      "-Xlint:_,-unused,-missing-interpolator",
      "-unchecked",
      "-deprecation",
      "-feature",
      // Later consider "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused-import",
      "-Yno-adapted-args",
      "-Ywarn-unused:_,-explicits,-implicits"
    )

    // Allow some behavior while interactively working on Scala code from the REPL
    private val scalacOptionsConsoleExclusions: Seq[String] = Seq(
      "-Xlint",
      "-Xfatal-warnings",
      "-Ywarn-unused-import"
    )

    private val scalacOptionsNotInTest: Seq[String] = Seq(
      "-Ywarn-value-discard"
    )

    def lintingSettings(failOnWarnings: Boolean = true, scalastyleExcludes: String = ""): Def.SettingsDefinition = {

      val scalacOptionsSettings =
        Seq(
          scalacOptions ++= scalacLintingSettings,
          Compile / scalacOptions ++= scalacOptionsNotInTest,
          Test / scalacOptions --= scalacOptionsNotInTest,
          Compile / console / scalacOptions --= scalacOptionsConsoleExclusions,
          Test / console / scalacOptions --= scalacOptionsConsoleExclusions
        )

      if (failOnWarnings) {
        scalacOptionsSettings ++ fatalWarningsExceptDeprecation ++ scalastyleSettings(scalastyleExcludes)
      } else {
        scalacOptionsSettings ++ scalastyleSettings(scalastyleExcludes)
      }
    }

    // Testing

    lazy val testingSettings: Def.SettingsDefinition = Seq(
      // Options from http://www.scalatest.org/user_guide/using_scalatest_with_sbt:
      //  - -o: output to stdout (default, but required for other flags)
      //  - D: Show durations of each test
      //  - F: Show full stack traces
      //  - -u: Causes test results to be written to junit-style xml files in the named directory so CI can pick it up
      Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF", "-u", "target/test-reports")
    )

    def coverageSettings(excludedPackages: String = "", minimumCoverage: Double = 80.00, failOnMinimum: Boolean = true): Def.SettingsDefinition = {
      Seq(
        coverageExcludedPackages := excludedPackages,
        coverageMinimum := minimumCoverage,
        coverageFailOnMinimum := failOnMinimum,
        coverageHighlighting := true
      )
    }

    // Publishing

    def publishSettings(publishingEnabled: Boolean): Def.SettingsDefinition = {
      if (publishingEnabled) {
        Seq(
          organization := "com.dotdata",
          // Don't generate docs in production builds
          Compile / doc / sources := Seq.empty,
          Compile / packageDoc / publishArtifact := false,
          publishMavenStyle := true,
          publishArtifact := true,
          publishTo := {
            // Repository internal caching
            val nexus = Option(System.getProperty("REPOSITORY_URL")).getOrElse("http://ec2-52-38-203-205.us-west-2.compute.amazonaws.com")
            if (isSnapshot.value) {
              Some(("snapshots" at nexus + "/repository/maven-snapshots;build.timestamp=" + new java.util.Date().getTime).withAllowInsecureProtocol(true))
            } else {
              Some(("releases" at nexus + "/repository/maven-releases").withAllowInsecureProtocol(true))
            }
          }
        )
      } else {
        Seq(
          organization := "com.dotdata",
          publishArtifact := false
        )
      }
    }

    def dotDataSettings(
        failOnWarnings: Boolean = true,
        testCoverage: Double = 80.00,
        publishingEnabled: Boolean = false,
        scalastyleExcludes: String = "",
    ): Def.SettingsDefinition = {
      compilerSettings() ++
        formatSettings ++
        lintingSettings(failOnWarnings, scalastyleExcludes) ++
        testingSettings ++
        coverageSettings(minimumCoverage = testCoverage) ++
        publishSettings(publishingEnabled)
    }

  }

}
