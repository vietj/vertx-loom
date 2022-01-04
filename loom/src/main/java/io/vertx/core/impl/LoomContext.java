package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.future.FutureInternal;
import io.vertx.core.impl.future.Listener;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A fork a WorkerContext with a couple of changes.
 */
public class LoomContext extends ContextImpl {

  public static LoomContext create(Vertx vertx, EventLoop nettyEventLoop, ThreadFactory threadFactory) {
    VertxImpl _vertx = (VertxImpl) vertx;
    LoomContext[] ref = new LoomContext[1];
    ExecutorService exec = Executors.newSingleThreadExecutor(threadFactory);
    LoomContext context = new LoomContext(_vertx, nettyEventLoop, _vertx.internalWorkerPool, new WorkerPool(exec, null), null, _vertx.closeFuture(), Thread.currentThread().getContextClassLoader(), true, threadFactory);
    exec.submit(() -> ContextInternal.local.set(context));
    ref[0] = context;
    return context;
  }

  private final ThreadFactory threadFactory;

  LoomContext(VertxInternal vertx,
              EventLoop eventLoop,
              WorkerPool internalBlockingPool,
              WorkerPool workerPool,
              Deployment deployment,
              CloseFuture closeFuture,
              ClassLoader tccl,
              boolean disableTCCL,
              ThreadFactory threadFactory) {
    super(vertx, eventLoop, internalBlockingPool, workerPool, deployment, closeFuture, tccl, disableTCCL);

    this.threadFactory = threadFactory;
  }

  @Override
  void runOnContext(AbstractContext ctx, Handler<Void> action) {
    try {
      run(ctx, null, action);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is a worker thread of this context the implementation will execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the worker thread for execution</li>
   * </ul>
   */
  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    execute2(argument, task);
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    execute2(argument, arg -> {
      ctx.dispatch(arg, task);
    });
  }

  @Override
  <T> void execute(AbstractContext ctx, Runnable task) {
    execute(this, task, Runnable::run);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  private <T> void run(ContextInternal ctx, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    workerPool.executor().execute(() -> {
      ctx.dispatch(value, task);
    });
  }

  private <T> void execute2(T argument, Handler<T> task) {
    if (Context.isOnWorkerThread()) {
      task.handle(argument);
    } else {
      workerPool.executor().execute(() -> {
        task.handle(argument);
      });
    }
  }

  @Override
  boolean inThread() {
    return Context.isOnWorkerThread();
  }

  @Override
  public ContextInternal duplicate() {
    // This is fine as we are running on event-loop
    return create(owner, nettyEventLoop(), threadFactory);
  }

  public <T> T await(FutureInternal<T> future) {
    ReentrantLock lock = new ReentrantLock();
    Condition cond = lock.newCondition();
    lock.lock();
    try {
      future.addListener(new Listener<T>() {
        @Override
        public void emitSuccess(ContextInternal context, T value) {
          lock.lock();
          try {
            cond.signal();
          } finally {
            lock.unlock();
          }
        }
        @Override
        public void emitFailure(ContextInternal context, Throwable failure) {
          lock.lock();
          try {
            cond.signal();
          } finally {
            lock.unlock();
          }
        }
        @Override
        public void onSuccess(T value) {
          throw new UnsupportedOperationException();
        }
        @Override
        public void onFailure(Throwable failure) {
          throw new UnsupportedOperationException();
        }
      });
      try {
        cond.await();
      } catch (InterruptedException e) {
        throw new VertxException(e);
      }
      if (future.succeeded()) {
        return future.result();
      } else {
        // ExecutionException
        throw new VertxException(future.cause());
      }
    } finally {
      lock.unlock();
    }
  }
}
