import scalariform.formatter.preferences._

organization := "me.arturopala"

name := "latex-service"

version := "0.1.0-SNAPSHOT"

resolvers += Resolver.mavenLocal

scalaVersion := "2.11.7"

val akkaVersion = "2.4.0"
val akkaHttpVersion = "2.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-core-experimental" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaHttpVersion,
  "io.spray" %%  "spray-json" % "1.3.2",
  "com.softwaremill.macwire" %% "macros" % "2.1.0" % "provided",
  "com.softwaremill.macwire" %% "util" % "2.1.0",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit-experimental" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpVersion % Test,
  "org.scalatest" %% "scalatest" % "2.2.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.12.5" % Test
).map(_.withSources())

com.typesafe.sbt.SbtScalariform.scalariformSettings

ScalariformKeys.preferences := PreferencesImporterExporter.loadPreferences(baseDirectory.value / "project" / "formatterPreferences.properties" toString)

Revolver.settings

EclipseKeys.skipParents in ThisBuild := false

mainClass in (Compile, run) := Some("latex.Boot")

lazy val root = (project in file(".")).enablePlugins(DockerPlugin)

fork := true

connectInput in run := true

outputStrategy := Some(StdoutOutput)

docker <<= docker.dependsOn(Keys.`package`.in(Compile, packageBin))

imageNames in docker := Seq(
  ImageName(s"arturopala/${name.value}:latest"), // Sets the latest tag
  ImageName(
    namespace = Some("arturopala"),
    repository = name.value,
    tag = Some("v" + version.value)
  ) // Sets a name with a tag that contains the project version
)

val dockerPort = 8080
val workspaceFolder = "/workspace"
val linuxUser = "latex"

dockerfile in docker := {
  val jarFile = artifactPath.in(Compile, packageBin).value
  val classpath = (managedClasspath in Compile).value
  val mainclass = mainClass.in(Compile, packageBin).value.getOrElse(sys.error("Expected exactly one main class"))
  val jarTarget = s"/app/${jarFile.getName}"
  // Make a colon separated classpath with the JAR file
  val classpathString = classpath.files.map("/app/" + _.getName).mkString(":") + ":" + jarTarget
  new Dockerfile {
    // Base image
    from("arturopala/scala:2.11.7")
    // Add all files on the classpath
    add(classpath.files, "/app/")
    // Add the JAR file
    add(jarFile, jarTarget)
    expose(dockerPort)
    expose(9999)    // for JMX  
    env("LATEX_LAB_WORKSPACE", workspaceFolder)
    env("LATEX_LAB_PORT", s"$dockerPort")
    run("mkdir", "-p", workspaceFolder)
    volume(workspaceFolder)
    //run(s"groupadd -r $linuxUser && useradd -r -g $linuxUser $linuxUser")
    //user(linuxUser)
    // On launch run Java with the classpath and the main class
    copy(file("../workspace/test"), file(s"$workspaceFolder/test"))
    entryPoint("java", "-cp", classpathString, mainclass)
  }
}

buildOptions in docker := BuildOptions(
  cache = false,
  removeIntermediateContainers = BuildOptions.Remove.Always
  //,pullBaseImage = BuildOptions.Pull.Always
)