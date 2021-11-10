package io.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.loom.core.VertxLoom;

public class Main {

  public static void main(String[] args) throws Exception {

    Vertx vertx = Vertx.vertx();

    VertxLoom vertxLoom = new VertxLoom(vertx);

    vertxLoom.virtual(() -> {
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> {
        System.out.println("Begin HTTP request on " + Thread.currentThread());
        vertx.setTimer(200, id -> {
          System.out.println("End HTTP request on " + Thread.currentThread());
          req.response().end("Hello Loom");
        });
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
