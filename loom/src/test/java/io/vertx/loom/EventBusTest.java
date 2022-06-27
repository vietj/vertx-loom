package io.vertx.loom;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.loom.core.VertxLoom;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

public class EventBusTest extends VertxTestBase {

  VertxLoom loom;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    loom = new VertxLoom(vertx);
  }

  @Test
  public void testEventBus() throws Exception {
    EventBus eb = vertx.eventBus();
    eb.consumer("test-addr", msg -> {
      msg.reply(msg.body());
    });
    loom.run(v -> {
      Message<String> ret = loom.await(eb.request("test-addr", "test"));
      assertEquals("test", ret.body());
      testComplete();
    });
    await();
  }
}
