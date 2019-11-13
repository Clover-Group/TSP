addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.4.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.4")
lazy val root = project.in(file(".")).dependsOn(ghReleasePlugin)
lazy val ghReleasePlugin = RootProject(uri("https://github.com/hyst329/sbt-github-release.git"))
