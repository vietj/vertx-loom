package io.vertx.loom;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.loom.core.VertxLoom;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AwaitBugTest {

  static final String FUT_VALUE = "hello world";

  public static Future<String> fut() {
    return Future.succeededFuture(FUT_VALUE);
  }

  @Test
  public void test() throws Exception {
    Vertx vertx = Vertx.vertx()
      .exceptionHandler(Throwable::printStackTrace);
    VertxLoom vertxLoom = new VertxLoom(vertx);

    CountDownLatch latch = new CountDownLatch(1);

    HttpServer server = vertx.createHttpServer();
    server.requestHandler(req -> {
      req.response().end(FUT_VALUE);
    });
    server.listen(8088, "localhost").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    vertxLoom.virtual(() -> {
      HttpClient client = vertx.createHttpClient();
      for (int i = 0; i < 100; ++i) {
        HttpClientRequest req = vertxLoom.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
        HttpClientResponse resp = vertxLoom.await(req.send());
        Buffer body = vertxLoom.await(resp.body());
        String bodyString = body.toString(StandardCharsets.UTF_8);
        if (!FUT_VALUE.equals(bodyString)) {
          throw new RuntimeException("Failed");
        };
      }
      latch.countDown();
    });
    latch.await();
  }
}
