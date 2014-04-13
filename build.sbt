scalaVersion := "2.10.4"

scalacOptions in ThisBuild ++= Seq("-target:jvm-1.7","-optimise","-feature","-deprecation","-Xlint","-Yinline-warnings")

// scalacOptions += "-Ymacro-debug-lite"

resolvers in ThisBuild += Resolver.file("Local repo", file(Path.userHome.absolutePath+"/.ivy2/local"))(Resolver.ivyStylePatterns)

scalacOptions in (Compile, doc) <++= baseDirectory.map { bd => Seq(
  "-sourcepath", bd.getAbsolutePath,
  "-doc-source-url", "https://github.com/databrary/databrary/tree/master€{FILE_PATH}.scala"
) }

play.Project.templatesImport ++= Seq("macros._", "site._")

GitDescribe.gitDescribeOptions in ThisBuild := Seq("--tags", "--dirty")

version in ThisBuild <<= GitDescribe.gitDescribe.apply(_.getOrElse("unknown"))

javascriptEntryPoints := PathFinder.empty // disable play's standard js compiler

resourceGenerators in Compile <+= AngularTemplate.Compiler

JSConcatCompiler.externs := Seq(url("http://code.jquery.com/jquery-1.11.0.min.js"))

resourceGenerators in Compile <+= JSConcatCompiler.Compiler
