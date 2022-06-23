package io.vertx.core.impl;

import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class Scheduler implements Executor {

  private static final ThreadFactory threadFactory = Thread.ofVirtual().name("vert.x-virtual-thread-", 0).factory();

  private final LinkedList<Task> queue = new LinkedList<>();
  private Thread current;

  private static class Task {
    private final Thread thread;
    private final Runnable runnable;

    public Task(Thread thread, Runnable runnable) {
      this.thread = thread;
      this.runnable = runnable;
    }
  }

  @Override
  public void execute(Runnable command) {
    Thread thread = threadFactory.newThread(() -> run(command));
    Task task = new Task(thread, thread::start);
    execute(task, false);
  }

  private void execute(Task task, boolean first) {
    synchronized (this) {
      if (current != null) {
        if (first) {
          queue.addFirst(task);
        } else {
          queue.addLast(task);
        }
        return;
      }
      current = task.thread;
    }
    task.runnable.run();
  }

  private void run(Runnable task) {
    task.run();
    runNext();
  }

  private void runNext() {
    Task next;
    synchronized (this) {
      next = queue.poll();
      if (next == null) {
        current = null;
        return;
      }
      current = next.thread;
    }
    next.runnable.run();
  }

  public <T> T await(CompletableFuture<T> fut) {
    Thread th = Thread.currentThread();
    synchronized (this) {
      if (current != th) {
        throw new IllegalStateException();
      }
    }
    runNext();
    CompletableFuture<T> latch = new CompletableFuture<>();
    fut.whenComplete((v, err) -> {
      execute(new Task(th, () -> {
        if (err == null) {
          latch.complete(v);
        } else {
          latch.completeExceptionally(err);
        }
      }), true);
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

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
