package dotdata.sbt

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin.autoImport._
import scoverage.ScoverageKeys._
import sbt.Keys._
import sbt._


object SbtConfig extends AutoPlugin {

  object autoImport {

    // Compiler settings

    private val scalacDefaultOptions = Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-encoding", "utf8")

    private val scalacLanguageFeatures = Seq(
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps"
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

    def compilerSettings(failOnWarnings: Boolean = false, versionOfScala: String = "2.11.11"): Seq[Setting[_]] = Seq(
      scalaVersion := versionOfScala,
      scalacOptions := (scalacDefaultOptions ++ scalacLanguageFeatures),
      scalacOptions ++= (if(failOnWarnings) Seq("-Xfatal-warnings") else Seq.empty),
      scalacOptions in Compile ++= scalacOptionsNotInTest,
      scalacOptions in Test --= scalacOptionsNotInTest,
      scalacOptions in (Compile, console) := (scalacDefaultOptions ++ scalacLanguageFeatures) diff scalacOptionsConsoleExclusions,
      scalacOptions in (Test, console) := (scalacDefaultOptions ++ scalacLanguageFeatures) diff scalacOptionsConsoleExclusions,
      fork := true
    )

    /* Following settings allow fatal warnings except for deprecations (which might be needed for migrations)
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
      }
     */


    // Formatting

    lazy val formatSettings: Def.SettingsDefinition = {
      val generateScalafmtConfTask = Def.task {
        val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
        val formatConfFile = resourceManaged.value / "scalafmt.conf"
        IO.delete(formatConfFile)
        IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
        formatConfFile
      }
      Seq(
        scalafmtConfig := generateScalafmtConfTask.value,
        // TODO: Discuss, scalafmtOnCompile := true and scalafmt on test
        scalafmt in Compile := {
          (scalafmt in Compile).dependsOn(scalafmt in Test).value
        },
        // scalafmt::test -> tests scalafmt format in src/main + src/test (added behavior)
        test in scalafmt in Compile := {
          (test in scalafmt in Compile).dependsOn(test in scalafmt in Test).value
        },
        test in Test := {
          (test in scalafmt in Compile).value
          (test in Test).value
        }
      )
    }


    // Linting

    val scalacLintingSettings: Def.SettingsDefinition = Seq(
      scalacOptions ++= {
        Seq(
          "-Xlint:_,-unused,-missing-interpolator",
          // "-Ywarn-numeric-widen", potentially enable
          "-Ywarn-dead-code",
          "-Ywarn-unused-import",
          "-Yno-adapted-args",
          "-Ywarn-unused:_,-explicits,-implicits"
        )
      }
    )


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

    lazy val lintingSettings: Def.SettingsDefinition = scalacLintingSettings ++ scalastyleSettings()


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


    def dotDataSettings(failOnWarnings: Boolean = false, testCoverage: Double = 80.00, publishingEnabled: Boolean = false): Def.SettingsDefinition = {
      compilerSettings(failOnWarnings) ++
        formatSettings ++ lintingSettings ++ testingSettings ++
        coverageSettings(minimumCoverage = testCoverage) ++
        publishSettings(publishingEnabled)
    }

  }

}
