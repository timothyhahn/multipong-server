import com.typesafe.sbt.SbtStartScript
seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "multipong-server"
 
version := "1.0"
 
scalaVersion := "2.10.3"

//CHANGE THIS LINE TO RUN A DIFFERENT PROJECT
mainClass in (Compile, run) := Some("multipongserver.ServerApp")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe snapshots" at "http://repo.typesafe.com/typesafe/snapshots/" 

libraryDependencies ++= List(
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "net.liftweb" %% "lift-json" % "2.5.1",
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "org.skife.com.typesafe.config" % "typesafe-config" % "0.3.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
)

//unmanagedBase := baseDirectory.value / "lib/artemis"

//unmanagedBase += baseDirectory.value / "lib/multipong"
