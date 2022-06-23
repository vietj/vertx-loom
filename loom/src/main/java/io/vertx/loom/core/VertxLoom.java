package io.vertx.loom.core;

import io.netty.channel.EventLoop;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.future.FutureInternal;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

public class VertxLoom {

  private final Vertx vertx;

  public VertxLoom(Vertx vertx) {
    this.vertx = vertx;
  }

  public void virtual(Runnable runnable) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    LoomContext context = LoomContext.create(vertx, eventLoop);
    context.runOnContext(v -> {
      runnable.run();
    });
  }

  public <T> T await(Future<T> future) {
    ContextInternal internal = (ContextInternal) vertx.getOrCreateContext();
    LoomContext ctx = (LoomContext) internal.unwrap();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await((FutureInternal<T>) future);
  }

  public <T> T await(Supplier<Future<T>> supplier) {
    LoomContext ctx = (LoomContext) vertx.getOrCreateContext();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await(supplier);
  }
}
