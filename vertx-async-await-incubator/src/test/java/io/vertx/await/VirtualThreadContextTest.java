package io.vertx.await;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.ContextInternal;
import io.vertx.await.impl.VirtualThreadContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class VirtualThreadContextTest extends VertxTestBase {

  Async async;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    async = new Async(vertx);
  }

  @Test
  public void testContext() {
    async.run(v -> {
      Thread thread = Thread.currentThread();
      assertTrue(thread.isVirtual());
      Context context = vertx.getOrCreateContext();
      assertTrue(context instanceof VirtualThreadContext);
      context.runOnContext(v2 -> {
        assertSame(thread, Thread.currentThread());
        assertSame(context, vertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAwaitFuture() {
    Object result = new Object();
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame(result, async.await(promise.future()));
      testComplete();
    });
    await();
  }

  @Test
  public void testAwaitCompoundFuture() {
    Object result = new Object();
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame("HELLO", async.await(promise.future().map(res -> "HELLO")));
      testComplete();
    });
    await();
  }

  @Test
  public void testDuplicateUseSameThread() {
    int num = 1000;
    waitFor(num);
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Thread th = Thread.currentThread();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          assertSame(th, Thread.currentThread());
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testDuplicateConcurrentAwait() {
    int num = 1000;
    waitFor(num);
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      Object lock = new Object();
      List<Promise<Void>> list = new ArrayList<>();
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v2 -> {
          Promise<Void> promise = duplicate.promise();
          boolean complete;
          synchronized (lock) {
            list.add(promise);
            complete = list.size() == num;
          }
          if (complete) {
            context.runOnContext(v3 -> {
              synchronized (lock) {
                list.forEach(p -> p.complete(null));
              }
            });
          }
          Future<Void> f = promise.future();
          async.await(f);
          complete();
        });
      }
    });
    await();
  }

  @Test
  public void testTimer() {
    async.run(v -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<String> promise = context.promise();
      vertx.setTimer(100, id -> {
        promise.complete("foo");
      });
      String res = async.await(promise);
      assertEquals("foo", res);
      testComplete();
    });
    await();
  }

  @Test
  public void testInThread() {
    async.run(v1 -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      assertTrue(context.inThread());
      new Thread(() -> {
        boolean wasNotInThread = !context.inThread();
        context.runOnContext(v2 -> {
          assertTrue(wasNotInThread);
          assertTrue(context.inThread());
          testComplete();
        });
      }).start();
    });
    await();
  }
}
