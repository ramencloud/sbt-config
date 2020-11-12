# dotData sbt config

![Build Status](https://github.com/ramencloud/sbt-config/workflows/Continuous%20Integration/badge.svg)

## Adding the plugin to your project

### Publish the plugin locally

    sbt publishLocal

### project/plugins.sbt

    addSbtPlugin("com.dotdata" % "sbt-config" % "<latest_version>")

Run `sbt reload` after adding the plugin.

### build.sbt

To use all standard settings, use the following format:

    lazy val myProject = (project in file("."))
      .settings(dotDataSettings(publishingEnabled = false))
      .settings(... other settings ...

To opt-out from some settings, use the following format:

    lazy val myProjectWithCustomizations = (project in file("."))
      .settings(compilerSettings())
      .settings(formatSettings ++ lintingSettings() ++ testingSettings)
      .settings(coverageSettings(minimumCoverage = 80.00))
      .settings(publishSettings(publishingEnabled = false))
      .settings(... other settings ...
