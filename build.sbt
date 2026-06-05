// ─── build.sbt ───────────────────────────────────────────────────────────────

name         := "auris"
organization := "co.auris"
version      := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    // ── Compiler options ────────────────────────────────────────────────────
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Xfatal-warnings"
    ),

    // ── Dependencies ────────────────────────────────────────────────────────
    libraryDependencies ++= Seq(

      // Play
      guice,
      ws,
      filters,

      // Database — Slick via Play integration
      "com.typesafe.play"      %% "play-slick"            % "5.3.1",
      "com.typesafe.play"      %% "play-slick-evolutions" % "5.3.1",

      // PostgreSQL JDBC driver
      "org.postgresql"          %  "postgresql"            % "42.7.3",

      // Slick-pg: adds Postgres-specific types (UUID, JSONB, arrays, enums)
      "com.github.tminglei"    %% "slick-pg"              % "0.22.2",
      "com.github.tminglei"    %% "slick-pg_play-json"    % "0.22.2",

      // Connection pool — HikariCP is bundled with Slick, config only needed

      // JWT — Pauldijou's jwt-scala (Play JSON variant)
      "com.github.jwt-scala"   %% "jwt-play-json"         % "10.0.1",

      // Password hashing — jBCrypt
      "org.mindrot"             %  "jbcrypt"               % "0.4",

      // Email — Play Mailer
      "com.typesafe.play"      %% "play-mailer"            % "9.0.0",
      "com.typesafe.play"      %% "play-mailer-guice"      % "9.0.0",

      // JSON — Play JSON already included; adds extra combinators
      "com.typesafe.play"      %% "play-json"              % "2.10.5",

      // Logging
      "ch.qos.logback"          %  "logback-classic"        % "1.5.6",

      // Testing
      "org.scalatestplus.play" %% "scalatestplus-play"     % "7.0.1"  % Test,
      "org.mockito"            %% "mockito-scala"           % "1.17.37" % Test,
      "com.h2database"          %  "h2"                     % "2.2.224" % Test,

      "com.beachape" %% "enumeratum"            % "1.7.3",
      "com.beachape" %% "enumeratum-play-json"  % "1.7.3",
    ),

    // ── Routes ──────────────────────────────────────────────────────────────
    play.sbt.routes.RoutesKeys.routesImport := Seq.empty,

    // ── Assets ──────────────────────────────────────────────────────────────
    // Vite handles all frontend assets — Play only serves the API
    // If you later want Play to serve static files from /public, re-enable:
    // PlayKeys.playRunHooks += ...,

    // ── Test ────────────────────────────────────────────────────────────────
    Test / javaOptions += "-Dconfig.file=conf/application.test.conf",
    Test / fork := true
  )

// ─── plugins.sbt ─────────────────────────────────────────────────────────────
// Create at project/plugins.sbt with:
//
// addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.4")
// addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.5.2")
