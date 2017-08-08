sonatypeProfileName := "com.workday"

publishMavenStyle := true

licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))

homepage := Some(url("https://github.com/Workday/elasticrypt"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/Workday/elasticrypt"),
    "scm:git@github.com:Workday/elasticrypt.git"
  )
)

developers := List(
  Developer(
    id    = "myzhou96",
    name  = "Michelle Y Zhou",
    email = "myzhou96@gmail.com",
    url   = url("https://github.com/Workday/elasticrypt")
  )
)

pomExtra in Global := {
    <licenses>
      <license>
        <name>MIT</name>
        <url>https://opensource.org/licenses/mit-license.php</url>
      </license>
    </licenses>

    <scm>
      <connection>scm:git:git://github.com/Workday/elasticrypt.git</connection>
      <developerConnection>scm:git:ssh://github.com:Workday/elasticrypt.git</developerConnection>
      <url>https://github.com/Workday/elasticrypt</url>
    </scm>

    <developers>
      <developer>
        <name>Michelle Y Zhou</name>
        <email>myz6@cornell.edu</email>
        <organization>Workday</organization>
        <organizationUrl>https://www.workday.com</organizationUrl>
      </developer>
    </developers>
}