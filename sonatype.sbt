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