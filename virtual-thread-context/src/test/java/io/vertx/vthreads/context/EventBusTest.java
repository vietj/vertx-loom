package io.vertx.vthreads.context;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

public class EventBusTest extends VertxTestBase {

  VThreads vthreads;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    vthreads = new VThreads(vertx);
  }

  @Test
  public void testEventBus() throws Exception {
    EventBus eb = vertx.eventBus();
    eb.consumer("test-addr", msg -> {
      msg.reply(msg.body());
    });
    vthreads.runOnVirtualThreads(v -> {
      Message<String> ret = vthreads.await(eb.request("test-addr", "test"));
      assertEquals("test", ret.body());
      testComplete();
    });
    await();
  }
}
