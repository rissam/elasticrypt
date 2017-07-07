import java.io.File

import sbt.Keys.{name, resourceGenerators, version, _}
import sbt._
import sbtassembly.Plugin.AssemblyKeys.assembly

val assembleZip = TaskKey[File]("assembleZip")
val zipArtifact = SettingKey[Artifact]("zipArtifact")

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
    ),
    assembleZip <<= (assembly, target, name, version) map {
      (assembledJar: File, target: File, name: String, version: String) =>
        val artifact = target / s"$name.zip"
        IO.write(target / "VERSION", version)

        val entries = Seq(
          (assembledJar, s"$name.jar"),
          (target / "VERSION", "VERSION")
        )

        IO.zip(entries, artifact)
        artifact
    },
    zipArtifact := Artifact(s"${name.value}", "zip", "zip"),

    resourceGenerators in Compile <+=
      (resourceManaged in Compile, name, version) map { (dir, n, v) =>
        val file = dir / "elasticrypt.properties"
        val contents =
          s"""
             |plugin=org.elasticsearch.plugins.ElasticryptPlugin
             |version=$v
            """.stripMargin
        IO.write(file, contents)
        Seq(file)
      }
  )
  .settings(addArtifact(zipArtifact, assembleZip).settings: _*)
