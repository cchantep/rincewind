lazy val server = project.in(file("server"))

lazy val writer = project.in(file("writer"))

lazy val reader = project.in(file("reader"))

lazy val rincewind = Project(id = "rincewind", base = file("."))
  .aggregate(server, writer, reader)

name := "rincewind"
