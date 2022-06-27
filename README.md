# Vert.x Loom incubator

Incubator for Loom prototypes.

This PoC is based on the [Async/Await support by August Nagro](https://github.com/AugustNagro/vertx-async-await).

## Vert.x Loom

Provides a `LoomContext` implementation of `io.vertx.core.Context` that runs Vert.x task on a virtual thread.

The `LoomContext` serializes task executions following as per the `Context` contract.

- can await Vert.x futures
- more meaningful stack traces
- cannot block on other JDK blocking constructs such as latches, locks, sleep, etc... (since it would imply to have a multi-threaded application)
- thread locals are only reliable within the execution of a context task

## Example

```java
VertxLoom.run(v -> {
  // Run on a Vert.x loom thread
  HttpServer server = vertx.createHttpServer();
  server.handler(request -> {
    request.response().end("Hello World");
  });
  VertxLoom.await(server.listen(8080, "localhost"));
  HttpClient client = vertx.createHttpClient();
  HttpClientRequest req = loom.await(client.request(HttpMethod.GET, 8080, "localhost", "/"));
  HttpClientResponse resp = loom.await(req.send());
  int status = resp.status();
  Buffer body = loom.await(resp.body());
});
```
