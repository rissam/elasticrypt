lazy val commonSettings = Seq(
  name := "elasticsearch-encryption-plug-in",
  organization := "com.workday",
  version := "1.7.0",
  crossScalaVersions := Seq("2.10.4", "2.11.8")
)

lazy val root = Project(id = "elasticsearch-encryption-plug-in", base = file("."))
  .settings(assemblySettings: _*)
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.elasticsearch" % "elasticsearch" % "1.7.5-77" % "provided"
    )
  )