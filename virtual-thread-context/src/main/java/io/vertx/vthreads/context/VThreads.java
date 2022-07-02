package io.vertx.vthreads.context;

import io.netty.channel.EventLoop;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.vthreads.context.impl.VirtualThreadContext;
import io.vertx.vthreads.context.impl.Scheduler;
import io.vertx.core.impl.future.FutureInternal;

public class VThreads {

  private final Vertx vertx;

  public VThreads(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * Run a task on a Loom context using a virtual thread
   */
  public void runOnVirtualThreads(Handler<Void> task) {
    EventLoop eventLoop = vertx.nettyEventLoopGroup().next();
    VirtualThreadContext context = VirtualThreadContext.create(vertx, eventLoop, new Scheduler());
    context.runOnContext(task);
  }

  public static <T> T await(Future<T> future) {
    ContextInternal internal = (ContextInternal) Vertx.currentContext();
    VirtualThreadContext ctx = (VirtualThreadContext) internal.unwrap();
    if (ctx == null) {
      throw new IllegalStateException();
    }
    return ctx.await((FutureInternal<T>) future);
  }
}
