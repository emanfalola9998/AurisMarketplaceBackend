// ─── app/co/auris/startup/StartupModule.scala ──────────────────────────────────
//
// Registered via play.modules.enabled in application.conf. Guice never
// constructs ConfigValidator on its own since nothing else depends on it —
// asEagerSingleton() is what makes its validation actually run at boot.

package co.auris.startup

import com.google.inject.AbstractModule

class StartupModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ConfigValidator]).asEagerSingleton()
  }
}
