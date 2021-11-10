package io.vertx.loom.core;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.future.FutureInternal;

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

  public <T> T await(Future<T> future) {
    try {
      return ((FutureInternal<T>)future).await();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }
}
