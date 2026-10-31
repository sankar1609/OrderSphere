package com.ordersphere.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A body over the limit never reaches the backend whole, however it is framed. */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.gateway.discovery.locator.enabled=false",
      "GATEWAY_MAX_REQUEST_BODY=1KB"
    })
class RequestBodyLimitFilterTest {

  private static final String CHUNKED = "Transfer-Encoding: chunked\r\n";

  static HttpServer backend;
  static final AtomicInteger requestsReceived = new AtomicInteger();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          requestsReceived.incrementAndGet();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    backend.start();
    registry.add("spring.cloud.gateway.routes[0].id", () -> "api");
    registry.add(
        "spring.cloud.gateway.routes[0].uri",
        () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/**");
  }

  @AfterAll
  static void stopBackend() {
    backend.stop(0);
  }

  @BeforeEach
  void resetCount() {
    requestsReceived.set(0);
  }

  /** Over a raw socket: an HTTP client would pick the framing itself. Returns the status code. */
  private int send(String headers, String body) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      OutputStream out = socket.getOutputStream();
      String head = "POST /api HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" + headers;
      out.write((head + "\r\n" + body).getBytes(StandardCharsets.US_ASCII));
      out.flush();
      String statusLine =
          new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
      return Integer.parseInt(statusLine.split(" ")[1]);
    }
  }

  /** A chunked body of {@code bytes} bytes, in pieces of 256. */
  private static String chunks(int bytes) {
    StringBuilder body = new StringBuilder();
    for (int sent = 0; sent < bytes; sent += 256) {
      body.append("100\r\n").append("x".repeat(256)).append("\r\n");
    }
    return body.append("0\r\n\r\n").toString();
  }

  private int sendWithLength(int bytes) throws IOException {
    return send("Content-Length: " + bytes + "\r\n", "x".repeat(bytes));
  }

  @Test
  void aBodyAtTheLimitIsForwarded() throws Exception {
    assertThat(sendWithLength(1024)).isEqualTo(200);
    assertThat(requestsReceived).hasValue(1);
  }

  @Test
  void aBodyOverTheLimitIsRefusedWithoutReachingTheBackend() throws Exception {
    assertThat(sendWithLength(1025)).isEqualTo(413);
    assertThat(requestsReceived).hasValue(0);
  }

  @Test
  void anOversizedBodyIsRefusedOnItsHeaderAlone() throws Exception {
    assertThat(send("Content-Length: 214748364800\r\n", "")).isEqualTo(413);
    assertThat(requestsReceived).hasValue(0);
  }

  @Test
  void aChunkedBodyWithinTheLimitIsForwarded() throws Exception {
    assertThat(send(CHUNKED, chunks(1024))).isEqualTo(200);
    assertThat(requestsReceived).hasValue(1);
  }

  @Test
  void aChunkedBodyOverTheLimitNeverReachesTheBackendWhole() throws Exception {
    assertThat(send(CHUNKED, chunks(4096))).isEqualTo(413);
    assertThat(requestsReceived).hasValue(0);
  }

  @Test
  void anUndersizedContentLengthCantHideALargeChunkedBody() throws Exception {
    assertThat(send("Content-Length: 2\r\n" + CHUNKED, chunks(4096))).isIn(400, 413);
    assertThat(requestsReceived).hasValue(0);
  }

  @Test
  void aPostWithoutABodyIsForwarded() throws Exception {
    assertThat(send("", "")).isEqualTo(200);
    assertThat(requestsReceived).hasValue(1);
  }
}
