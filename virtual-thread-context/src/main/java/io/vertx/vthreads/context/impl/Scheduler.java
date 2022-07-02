package io.vertx.vthreads.context.impl;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * A scheduler that run tasks on virtual threads that can await futures.
 *
 * The scheduler serialize task execution on virtual threads.
 *
 * When a task is submitted, a virtual thread is started to execute the task, this thread will continue
 * executing tasks until the task queue is empty.
 *
 * A task can await a future, when it happens, a new virtual thread is started to continue task execution.
 *
 * When an awaited future is completed, the thread awaiting the future preempts the execution and the current virtual
 * thread executing tasks is stopped.
 */
public class Scheduler implements Executor {

  private static final ThreadFactory threadFactory = Thread.ofVirtual().name("vert.x-virtual-thread-", 0).factory();

  private final LinkedList<Runnable> tasks = new LinkedList<>();
  private Thread current;
  private final ThreadLocal<Boolean> inThread = new ThreadLocal<>();

  @Override
  public void execute(Runnable command) {
    Thread toStart;
    synchronized (this) {
      tasks.addLast(command);
      if (current != null) {
        return;
      }
      toStart = threadFactory.newThread(this::run);
      current = toStart;
    }
    toStart.start();
  }

  private void run() {
    while (true) {
      Runnable cmd;
      synchronized (Scheduler.this) {
        if (current != Thread.currentThread()) {
          break;
        }
        cmd = tasks.poll();
        if (cmd == null) {
          current = null;
          break;
        }
      }
      inThread.set(true);
      try {
        cmd.run();
      } finally {
        inThread.set(false);
      }
    }
  }

  public boolean inThread() {
    return inThread.get() == Boolean.TRUE;
  }

  public <T> T await(CompletableFuture<T> fut) {
    Thread th = Thread.currentThread();
    Thread toStart;
    synchronized (this) {
      if (current != th) {
        throw new IllegalStateException();
      }
      if (tasks.size() > 0) {
        toStart = threadFactory.newThread(this::run);
      } else {
        toStart = null;
      }
      current = toStart;
    }
    if (toStart != null) {
      toStart.start();
    }
    CompletableFuture<T> latch = new CompletableFuture<>();
    fut.whenComplete((v, err) -> {
      synchronized (Scheduler.this) {
        if (current != null) {
          tasks.addFirst(() -> {
            synchronized (Scheduler.this) {
              current = th;
            }
            doComplete(v, err, latch);
          });
          return;
        }
        current = th;
      }
      doComplete(v, err, latch);
    });
    try {
      return latch.get();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throwAsUnchecked(e);
      return null;
    }
  }

  private static <T> void doComplete(T val, Throwable err, CompletableFuture<T> fut) {
    if (err == null) {
      fut.complete(val);
    } else {
      fut.completeExceptionally(err);
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
