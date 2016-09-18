name := "elly"
version := "1.3"

scalaVersion := "2.11.8"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.10",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.10",
  "org.json4s" %% "json4s-native" % "3.4.0",
  "de.heikoseeberger" %% "akka-http-circe" % "1.10.0"
)