package io.vertx.loom;

import io.vertx.core.impl.Scheduler;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class ThreadTest extends VertxTestBase {


  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testScheduler() {
    disableThreadChecks();
    CompletableFuture<String> cf1 = new CompletableFuture<>();
    Scheduler scheduler = new Scheduler();
    scheduler.execute(() -> {
      System.out.println("1");
      sleep();
    });
    scheduler.execute(() -> {
      System.out.println("2.1");
      String s = scheduler.await(cf1);
      System.out.println("2.2 " + s);
      s = scheduler.await(cf1);
      System.out.println("2.2 " + s);
      testComplete();
    });
    scheduler.execute(() -> {
      System.out.println("3");
      sleep();
      cf1.complete("abc");
    });
    scheduler.execute(() -> {
      System.out.println("4");
      sleep();
    });
    await();
  }

}
