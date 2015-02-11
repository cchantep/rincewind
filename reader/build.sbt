name := "rincewind-reader"

version := "1.0.0"

scalaVersion := "2.11.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-remote" % "2.3.9",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "org.eclipse.jetty" % "jetty-servlets" % "9.2.6.v20141205",
  "org.eclipse.jetty" % "jetty-webapp" % "9.2.6.v20141205",
  "org.eclipse.jetty" % "jetty-continuation" % "9.2.6.v20141205",
  "io.argonaut" %% "argonaut" % "6.1-M5"  
)

