package io.vertx.loom;

import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.LoomContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.loom.core.VertxLoom;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class LoomContextTest extends VertxTestBase {

  VertxLoom loom;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    loom = new VertxLoom(vertx);
  }

  @Test
  public void testContext() {
    loom.virtual(() -> {
      Thread thread = Thread.currentThread();
      assertTrue(thread.isVirtual());
      Context context = vertx.getOrCreateContext();
      assertTrue(context instanceof LoomContext);
      context.runOnContext(v -> {
        assertTrue(thread.isVirtual());
        assertSame(context, vertx.getOrCreateContext());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testAwaitFuture() {
    Object result = new Object();
    loom.virtual(() -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame(result, loom.await(promise.future()));
      testComplete();
    });
    await();
  }

  @Test
  public void testAwaitFailure() {
    Exception cause = new Exception();
    loom.virtual(() -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.fail(cause);
      }).start();
      try {
        loom.await(promise.future());
        fail();
      } catch (Exception e) {
        assertSame(cause, e);
      }
      testComplete();
    });
    await();
  }
  @Test
  public void testAwaitCompoundFuture() {
    Object result = new Object();
    loom.virtual(() -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      PromiseInternal<Object> promise = context.promise();
      new Thread(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
        promise.complete(result);
      }).start();
      assertSame("HELLO", loom.await(promise.future().map(res -> "HELLO")));
      testComplete();
    });
    await();
  }

  @Test
  public void testDuplicate() {
    loom.virtual(() -> {
      ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
      int num = 1000;
      CyclicBarrier barrier = new CyclicBarrier(num);
      CountDownLatch latch = new CountDownLatch(num);
      for (int i = 0;i < num;i++) {
        ContextInternal duplicate = context.duplicate();
        duplicate.runOnContext(v -> {
          try {
            barrier.await();
          } catch (Exception e) {
            fail(e);
          }
          latch.countDown();
        });
      }
      try {
        latch.await();
      } catch (Exception e) {
        fail(e);
      }
      testComplete();
    });
    await();
  }
}
