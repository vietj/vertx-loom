package io.vertx.loom.core;

import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.Scheduler;
import io.vertx.core.impl.future.FutureInternal;

public class VertxLoom {

  private final Vertx vertx;

  public VertxLoom(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Run a task on a Loom context using a virtual thread
   */
  public void run(Handler<Void> task) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    LoomContext context = LoomContext.create(vertx, eventLoop, new Scheduler());
    context.runOnContext(task);
  }

  public <T> T await(Future<T> future) {
    ContextInternal internal = (ContextInternal) vertx.getOrCreateContext();
    LoomContext ctx = (LoomContext) internal.unwrap();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await((FutureInternal<T>) future);
  }
}
