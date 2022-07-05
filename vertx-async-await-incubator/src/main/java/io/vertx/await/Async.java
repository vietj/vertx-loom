package io.vertx.await;

import io.netty.channel.EventLoop;
import io.vertx.await.impl.VirtualThreadContext;
import io.vertx.await.impl.Scheduler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.FutureInternal;

public class Async {

  private final Vertx vertx;

  public Async(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Run a task on a virtual thread
   */
  public void run(Handler<Void> task) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    VirtualThreadContext context = VirtualThreadContext.create(vertx, eventLoop, new Scheduler());
    context.runOnContext(task);
  }

  public <T> T await(Future<T> future) {
    ContextInternal internal = (ContextInternal) vertx.getOrCreateContext();
    VirtualThreadContext ctx = (VirtualThreadContext) internal.unwrap();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await((FutureInternal<T>) future);
  }
}
