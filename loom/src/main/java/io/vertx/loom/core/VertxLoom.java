package io.vertx.loom.core;

import io.vertx.core.Vertx;
import io.vertx.core.impl.LoomContext;

import java.util.concurrent.ThreadFactory;

public class VertxLoom {

  private final Vertx vertx;
  private final ThreadFactory threadFactory;

  public VertxLoom(Vertx vertx) {
    this.vertx = vertx;
    this.threadFactory = Thread.ofVirtual().name("test-foo").factory();
  }

  public void virtual(Runnable runnable) {
    LoomContext context = LoomContext.create(vertx, vertx.nettyEventLoopGroup().next(), threadFactory);
    context.runOnContext(v -> {
      runnable.run();
    });
  }
}
