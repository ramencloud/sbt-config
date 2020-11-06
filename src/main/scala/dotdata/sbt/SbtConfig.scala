package dotdata.sbt

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import sbt.Keys._
import sbt._
import scala.collection.JavaConverters._

/**
  * @see https://engineering.sharethrough.com/blog/2015/09/23/capturing-common-config-with-an-sbt-parent-plugin/
  */
object SbtConfig extends AutoPlugin {

  object autoImport {

    lazy val formatSettings = {
      val generateScalafmtConfTask = Def.task {
        val scalafmtConfStream = getClass.getClassLoader.getResourceAsStream("scalafmt.conf")
        val formatConfFile = resourceManaged.value / "scalafmt.conf"
        IO.delete(formatConfFile)
        IO.write(formatConfFile, IO.readBytes(scalafmtConfStream))
        formatConfFile
      }
      Seq(
        scalafmtConfig := generateScalafmtConfTask.value,
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

    val scalacLintingSettings = Seq(
      scalacOptions ++= {
        Seq(
          "-Xlint:_,-unused,-missing-interpolator",
          "-Ywarn-numeric-widen",
          "-Ywarn-dead-code",
          "-Ywarn-unused:_,-explicits,-implicits"
        )
      },
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
    )

    lazy val lintingSettings = scalacLintingSettings

    private val scalacDefaultOptions = Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-encoding", "utf8")

    private val scalacLanguageFeatures = Seq(
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls"
    )


    lazy val compilerSettings: Seq[Setting[_]] = Seq(
      scalacOptions := (scalacDefaultOptions ++ scalacLanguageFeatures),
      scalacOptions in (Compile, console) := (scalacDefaultOptions ++ scalacLanguageFeatures),
      scalacOptions in (Compile, consoleQuick) := (scalacDefaultOptions ++ scalacLanguageFeatures),
      scalacOptions in (Compile, consoleProject) := (scalacDefaultOptions ++ scalacLanguageFeatures),
      fork := true
    )
  }







}
