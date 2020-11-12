package dotdata.sbt

import org.scalafmt.sbt.ScalafmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin.autoImport._
import scoverage.ScoverageKeys._
import sbt.Keys._
import sbt._
import scala.collection.JavaConverters._

object SbtConfig extends AutoPlugin {

  object autoImport {

    // Compiler settings

    def compilerSettings(versionOfScala: String = "2.11.11"): Def.SettingsDefinition = {
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

    lazy val formatSettings: Def.SettingsDefinition = {
      val generateScalafmtConfTask = Def.task {
        val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
        val formatConfFile     = resourceManaged.value / "scalafmt.conf"
        IO.delete(formatConfFile)
        IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
        formatConfFile
      }
      Seq(
        scalafmtConfig := generateScalafmtConfTask.value,
        test in Test := { // Configuration below for formatting "main" and "test" folders on `sbt test`
          (Compile / scalafmt).value
          (Test / scalafmt).value
          (test in Test).value
        }
      )
    }


    // Linting

    lazy val testScalastyle = taskKey[Unit]("testScalastyle")

    def scalastyleSettings(excludes: String = ""): Def.SettingsDefinition = {
      val generateScalastyleConfTask = Def.task {
        val stream    = getClass.getClassLoader.getResourceAsStream("scalastyle-config.xml")
        val styleFile = resourceManaged.value / "scalastyle-config.xml"
        IO.delete(styleFile)
        IO.write(styleFile, IO.readBytes(stream))
        Seq(styleFile)
      }

      if (excludes.nonEmpty) {
        Seq(
          (scalastyleSources in Compile) := {
            ((scalaSource in Compile).value ** "*.scala").get.filterNot(_.getAbsolutePath.contains(excludes))
          },
          (scalastyleSources in Test) := {
            ((scalaSource in Test).value ** "*.scala").get.filterNot(_.getAbsolutePath.contains(excludes))
          }
        )
      } else {
        Seq.empty
      }

      Seq(
        resourceGenerators in Compile += generateScalastyleConfTask.taskValue,
        scalastyleConfig := resourceManaged.value / "scalastyle-config.xml",
        testScalastyle := scalastyle.in(Compile).toTask("").value,
        testScalastyle in (Test, test) := {
          generateScalastyleConfTask.value
          (testScalastyle in (Test, test)).value
        },
        testExecution in (Test, test) := {
          generateScalastyleConfTask.value
          (testScalastyle in Compile).value
          (testScalastyle in Test).value
          (testExecution in (Test, test)).value
        }
      )
    }

    val fatalWarningsExceptDeprecation: Def.SettingsDefinition = Seq( // Deprecations are not immediate and need a notice
      compile in Compile := {

        val compiled = (compile in Compile).value
        val problems = compiled.readSourceInfos().getAllSourceInfos.asScala.flatMap {
          case (_, info) =>
            info.getReportedProblems
        }

        val deprecationsOnly = problems.forall { problem =>
          problem.message().contains("is deprecated")
        }

        if (!deprecationsOnly) sys.error("Fatal warnings: some warnings other than deprecations were found.")
        compiled
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

    def lintingSettings(failOnWarnings: Boolean = true): Def.SettingsDefinition = {

      val scalacOptionsSettings =
        Seq(
          scalacOptions ++= scalacLintingSettings,
          scalacOptions in Compile ++= scalacOptionsNotInTest,
          scalacOptions in Test --= scalacOptionsNotInTest,
          scalacOptions in (Compile, console) --= scalacOptionsConsoleExclusions,
          scalacOptions in (Test, console) --= scalacOptionsConsoleExclusions
        )

      if (failOnWarnings) {
        scalacOptionsSettings ++ fatalWarningsExceptDeprecation ++ scalastyleSettings()
      } else {
        scalacOptionsSettings ++ scalastyleSettings()
      }
    }

    // Testing

    lazy val testingSettings: Def.SettingsDefinition = Seq(
      // Options from http://www.scalatest.org/user_guide/using_scalatest_with_sbt:
      //  - -o: output to stdout (default, but required for other flags)
      //  - D: Show durations of each test
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
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
          organization := "com.dotdata", // TODO: or "dotdata"
          publishMavenStyle := true,
          publishTo := {
            val nexus = Option(System.getProperty("REPOSITORY_URL")).getOrElse("http://ec2-52-38-203-205.us-west-2.compute.amazonaws.com") // TODO: Discuss
            if (isSnapshot.value) {
              Some(("snapshots" at nexus + "/repository/maven-snapshots;build.timestamp=" + new java.util.Date().getTime).withAllowInsecureProtocol(true))
            } else {
              Some(("releases" at nexus + "/repository/maven-releases").withAllowInsecureProtocol(true))
            }
          }
        )
      } else {
        Seq(
          organization := "com.dotdata", // TODO: or "dotdata"
          publish := {},
          publishLocal := {}
        )
      }
    }

    def dotDataSettings(failOnWarnings: Boolean = true, testCoverage: Double = 80.00, publishingEnabled: Boolean = false): Def.SettingsDefinition = {
      compilerSettings() ++
        formatSettings ++ lintingSettings(failOnWarnings) ++ testingSettings ++
        coverageSettings(minimumCoverage = testCoverage) ++
        publishSettings(publishingEnabled)
    }

  }

}
