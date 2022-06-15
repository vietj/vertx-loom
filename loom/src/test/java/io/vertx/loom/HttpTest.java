package io.vertx.loom;

import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.loom.core.VertxLoom;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class HttpTest extends VertxTestBase {

  VertxLoom loom;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    loom = new VertxLoom(vertx);
  }

  @Test
  public void testDuplicate() throws Exception {
    int num = 1000;
    CountDownLatch latch = new CountDownLatch(1);
    loom.virtual(() -> {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setInitialSettings(new Http2Settings().setMaxConcurrentStreams(num)));
      CyclicBarrier barrier = new CyclicBarrier(num);
      server.requestHandler(req -> {
        try {
          barrier.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          fail(e);
        } catch (BrokenBarrierException e) {
          fail(e);
        }
        req.response().end("Hello World");
      });
      server.listen(8080, "localhost", onSuccess(v -> {
        latch.countDown();
      }));
    });
    awaitLatch(latch);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setProtocolVersion(HttpVersion.HTTP_2)
      .setHttp2ClearTextUpgrade(false)
    );
    waitFor(num);
    for (int i = 0;i < num;i++) {
      client
        .request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req.send().compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          complete();
      }));
    }
    await();
  }

}