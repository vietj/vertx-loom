package io.vertx.loom.core;

import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.future.FutureInternal;

import java.util.concurrent.ThreadFactory;

public class VertxLoom {

  private final Vertx vertx;

  public VertxLoom(Vertx vertx) {
    this.vertx = vertx;
  }

  public void virtual(Runnable runnable) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    ThreadFactory threadFactory = Thread.ofVirtual().name("vertx-loom").scheduler(eventLoop).factory();
    LoomContext context = LoomContext.create(vertx, eventLoop, threadFactory);
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
