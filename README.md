# dotData sbt config

## Adding the plugin to your project

### project/plugins.sbt

    addSbtPlugin("com.dotdata" % "sbt-config" % "<latest_version>")

Run `sbt reload` after adding a plugin.

### build.sbt

    lazy val myProject = (project in file("."))
      .settings(dotDataSettings(publishingEnabled = false))
      .settings(... other settings ...
