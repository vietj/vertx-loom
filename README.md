# Vert.x Loom incubator

Incubator for Loom prototypes.

This PoC is based on the [Async/Await support by August Nagro](https://github.com/AugustNagro/vertx-async-await).

## Vert.x Loom

Use virtual threads to write asynchronous Vert.x code that looks like it is synchronous.

- await Vert.x futures
- more meaningful stack traces

You still write Vert.x code with events, but you have the opportunity to write synchronous code for complex
workflows and use thread locals in such workflows.

### Example

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

### What this is about

Await Vert.x futures.

### What this is not about

Blocking on other JDK blocking constructs such as latches, locks, sleep, etc... (since it would imply to have a multi-threaded application).

### What you get

Vert.x default application model dispatches events on the event-loop.

```java
request.send(response -> {
  // Set the buffer for handlers
  response.handler(buffers -> {

  });
});
```

Using virtual threads with Vert.x requires to run application tasks on a virtual threads

```java
Thread.startVirtualThread(() -> {
  CompletableFuture<HttpClientResponse> fut = new CompletableFuture<>();
  request.send(response -> {
    fut.complete(response);
  });
  HttpClientResponse response = fut.get();
  // As we get the response the virtual thread, there is a window of time where the event-loop thread
  // as already sent buffers and we lost these events
  response.handler(buffer -> {

  });
});
```

This project implements a Vert.x Context with virtual threads in order to provide a race free model. All events are
dispatched to the same virtual thread, so when a virtual thread waits for an asynchronous result, the next events
remain in the context execution queue until the virtual thread is resumed.

```java
Future<HttpClientResponse> fut = request.send();
HttpClientResponse response = await(fut);
// Buffer events might be in the queue and if they are, they will be dispatched next
response.handler(buffer -> {

});
```

When a virtual thread awaits a future, a new virtual thread can be started to handle new events and avoid potential
self dead-locks, e.g in the following example, awaiting the response does not prevent the timer to fire

```java
Promise<HttpClientResponse> promise = context.promise();
vertx.setTimer(timeout, id -> promise.tryFail("Too late"));
request.send().onComplete(promise);
try {
  HttpClientResponse response = await(promise.future());
} catch (Exception timeout) {
  // Too late
}
```

### Thread local support

Thread locals are only reliable within the execution of a context task.

```java
ThreadLocal<String> local = new ThreadLocal();
local.set(userId);
HttpClientRequest req = loom.await(client.request(HttpMethod.GET, 8080, "localhost", "/"));
HttpClientResponse resp = loom.await(req.send());
// Thread local remains the same since it's the same virtual thread
```

### How it works

`LoomContext` implements `io.vertx.core.Context` and runs Vert.x task on virtual threads.

Like other context implementations `LoomContext` serializes tasks, so that events are serialized on the virtual thread.

When the virtual thread awaits a future, the virtual thread is parked and a new virtual thread can be started to continue handling tasks

When a future is completed, the virtual thread is unparked and is executed next (the future might be completed by a context task like a timer)
and preempts all the context pending tasks.
