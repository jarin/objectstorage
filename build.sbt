scalaVersion := "2.11.7"

name:= "objectstorage"


organization:="manifold.no"

version := "0.9"

libraryDependencies ++= Seq(
 "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
 "io.argonaut" %% "argonaut" % "6.1",
 "org.constretto" %% "constretto-scala" % "1.1"
)

scalacOptions += "-feature"

initialCommands in console := "import dispatch._,Defaults._ "
