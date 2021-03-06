resolvers += Resolver.githubPackages("idio")
githubTokenSource := TokenSource.GitConfig("github.token")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")
addSbtPlugin("org.idio" % "sbt-assembly-log4j2" % "2.0.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")