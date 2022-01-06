package io.vertx.loom.core;

import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.future.FutureInternal;

import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VertxLoom {

  private final Vertx vertx;

  public VertxLoom(Vertx vertx) {
    this.vertx = vertx;
  }

  public void virtual(Runnable runnable) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    // Use a single carrier thread for virtual threads
    ExecutorService exec = Executors.newSingleThreadExecutor();
    // Use this until the thread factory can be specified
    ThreadFactory threadFactory;
    try {
      var vtf = Class.forName("java.lang.ThreadBuilders").getDeclaredClasses()[0];
      Constructor constructor = vtf.getDeclaredConstructors()[0];
      constructor.setAccessible(true);
      threadFactory = (ThreadFactory) constructor.newInstance(new Object[] { exec, "vert.x-virtual-thread-", 0, 0, null });
    } catch (Exception e) {
      throw new VertxException(e);
    }
    LoomContext context = LoomContext.create(vertx, eventLoop, threadFactory);
    context.runOnContext(v -> {
      runnable.run();
    });
  }

  public <T> T await(Future<T> future) {
    LoomContext ctx = (LoomContext) vertx.getOrCreateContext();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await((FutureInternal<T>) future);
  }
}
