package io.vertx;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.loom.core.VertxLoom;

/**
 * Run this with {@code --enable-preview --add-opens java.base/java.lang=ALL-UNNAMED}.
 */
public class Main {

  public static void main(String[] args) throws Exception {

    Vertx vertx = Vertx.vertx();

    VertxLoom vertxLoom = new VertxLoom(vertx);

    vertxLoom.run(v -> {
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> {
        System.out.println("Begin HTTP request on " + Thread.currentThread());
        Promise<String> promise = ((ContextInternal)vertx.getOrCreateContext()).promise();

        // Async stuff
        new Thread(() -> {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          promise.complete("Hello Loom");
        }).start();
        String result = vertxLoom.await(promise.future());
        System.out.println("End HTTP request on " + Thread.currentThread());
        req.response().end(result);
      });
      server.listen(8080, "localhost").onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("Started on " + Thread.currentThread());
        } else {
          ar.cause().printStackTrace();
        }
      });
    });
    System.in.read();
  }
}
