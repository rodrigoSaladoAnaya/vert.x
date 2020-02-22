/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.netty.handler.codec.TooLongFrameException;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.WriteStream;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.CheckingSender;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.verticles.SimpleServer;
import io.vertx.test.core.TestUtils;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class Http1xTest extends HttpTest {

  @Override
  protected VertxOptions getOptions() {
    VertxOptions options = super.getOptions();
    options.getAddressResolverOptions().setHostsValue(Buffer.buffer("" +
      "127.0.0.1 localhost\n" +
      "127.0.0.1 host0\n" +
      "127.0.0.1 host1\n" +
      "127.0.0.1 host2\n"));
    return options;
  }

  @Test
  public void testClientOptions() {
    HttpClientOptions options = new HttpClientOptions();

    assertEquals(NetworkOptions.DEFAULT_SEND_BUFFER_SIZE, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    assertIllegalArgumentException(() -> options.setSendBufferSize(0));
    assertIllegalArgumentException(() -> options.setSendBufferSize(-123));

    assertEquals(NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(0));
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(-123));

    assertTrue(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(false));
    assertFalse(options.isReuseAddress());

    assertEquals(NetworkOptions.DEFAULT_TRAFFIC_CLASS, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    assertIllegalArgumentException(() -> options.setTrafficClass(-2));
    assertIllegalArgumentException(() -> options.setTrafficClass(256));

    assertTrue(options.isTcpNoDelay());
    assertEquals(options, options.setTcpNoDelay(false));
    assertFalse(options.isTcpNoDelay());

    boolean tcpKeepAlive = false;
    assertEquals(tcpKeepAlive, options.isTcpKeepAlive());
    assertEquals(options, options.setTcpKeepAlive(!tcpKeepAlive));
    assertEquals(!tcpKeepAlive, options.isTcpKeepAlive());

    int soLinger = -1;
    assertEquals(soLinger, options.getSoLinger());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSoLinger(rand));
    assertEquals(rand, options.getSoLinger());
    assertIllegalArgumentException(() -> options.setSoLinger(-2));

    assertEquals(0, options.getIdleTimeout());
    assertEquals(TimeUnit.SECONDS, options.getIdleTimeoutUnit());
    assertEquals(options, options.setIdleTimeout(10));
    assertEquals(options, options.setIdleTimeoutUnit(TimeUnit.MILLISECONDS));
    assertEquals(10, options.getIdleTimeout());
    assertEquals(TimeUnit.MILLISECONDS, options.getIdleTimeoutUnit());
    assertIllegalArgumentException(() -> options.setIdleTimeout(-1));

    assertFalse(options.isSsl());
    assertEquals(options, options.setSsl(true));
    assertTrue(options.isSsl());

    assertNull(options.getKeyCertOptions());
    JksOptions keyStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setKeyStoreOptions(keyStoreOptions));
    assertEquals(keyStoreOptions, options.getKeyCertOptions());

    assertNull(options.getTrustOptions());
    JksOptions trustStoreOptions = new JksOptions().setPath(TestUtils.randomAlphaString(100)).setPassword(TestUtils.randomAlphaString(100));
    assertEquals(options, options.setTrustStoreOptions(trustStoreOptions));
    assertEquals(trustStoreOptions, options.getTrustOptions());

    assertFalse(options.isTrustAll());
    assertEquals(options, options.setTrustAll(true));
    assertTrue(options.isTrustAll());

    assertTrue(options.isVerifyHost());
    assertEquals(options, options.setVerifyHost(false));
    assertFalse(options.isVerifyHost());

    assertEquals(5, options.getMaxPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMaxPoolSize(rand));
    assertEquals(rand, options.getMaxPoolSize());
    assertIllegalArgumentException(() -> options.setMaxPoolSize(0));
    assertIllegalArgumentException(() -> options.setMaxPoolSize(-1));

    assertTrue(options.isKeepAlive());
    assertEquals(options, options.setKeepAlive(false));
    assertFalse(options.isKeepAlive());

    assertFalse(options.isPipelining());
    assertEquals(options, options.setPipelining(true));
    assertTrue(options.isPipelining());

    assertEquals(HttpClientOptions.DEFAULT_PIPELINING_LIMIT, options.getPipeliningLimit());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setPipeliningLimit(rand));
    assertEquals(rand, options.getPipeliningLimit());
    assertIllegalArgumentException(() -> options.setPipeliningLimit(0));
    assertIllegalArgumentException(() -> options.setPipeliningLimit(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_MAX_POOL_SIZE, options.getHttp2MaxPoolSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2MaxPoolSize(rand));
    assertEquals(rand, options.getHttp2MaxPoolSize());
    assertIllegalArgumentException(() -> options.setHttp2MaxPoolSize(0));
    assertIllegalArgumentException(() -> options.setHttp2MaxPoolSize(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_MULTIPLEXING_LIMIT, options.getHttp2MultiplexingLimit());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2MultiplexingLimit(rand));
    assertEquals(rand, options.getHttp2MultiplexingLimit());
    assertIllegalArgumentException(() -> options.setHttp2MultiplexingLimit(0));
    assertEquals(options, options.setHttp2MultiplexingLimit(-1));
    assertEquals(-1, options.getHttp2MultiplexingLimit());

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE, options.getHttp2ConnectionWindowSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setHttp2ConnectionWindowSize(rand));
    assertEquals(rand, options.getHttp2ConnectionWindowSize());
    assertEquals(options, options.setHttp2ConnectionWindowSize(-1));
    assertEquals(-1, options.getHttp2ConnectionWindowSize());

    assertEquals(60000, options.getConnectTimeout());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setConnectTimeout(rand));
    assertEquals(rand, options.getConnectTimeout());
    assertIllegalArgumentException(() -> options.setConnectTimeout(-2));

    assertFalse(options.isTryUseCompression());
    assertEquals(options, options.setTryUseCompression(true));
    assertEquals(true, options.isTryUseCompression());

    assertTrue(options.getEnabledCipherSuites().isEmpty());
    assertEquals(options, options.addEnabledCipherSuite("foo"));
    assertEquals(options, options.addEnabledCipherSuite("bar"));
    assertNotNull(options.getEnabledCipherSuites());
    assertTrue(options.getEnabledCipherSuites().contains("foo"));
    assertTrue(options.getEnabledCipherSuites().contains("bar"));

    assertEquals(HttpVersion.HTTP_1_1, options.getProtocolVersion());
    assertEquals(options, options.setProtocolVersion(HttpVersion.HTTP_1_0));
    assertEquals(HttpVersion.HTTP_1_0, options.getProtocolVersion());
    assertIllegalArgumentException(() -> options.setProtocolVersion(null));

    assertEquals(HttpClientOptions.DEFAULT_MAX_CHUNK_SIZE, options.getMaxChunkSize());
    assertEquals(options, options.setMaxChunkSize(100));
    assertEquals(100, options.getMaxChunkSize());

    assertEquals(HttpClientOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH, options.getMaxInitialLineLength());
    assertEquals(options, options.setMaxInitialLineLength(100));
    assertEquals(100, options.getMaxInitialLineLength());

    assertEquals(HttpClientOptions.DEFAULT_MAX_HEADER_SIZE, options.getMaxHeaderSize());
    assertEquals(options, options.setMaxHeaderSize(100));
    assertEquals(100, options.getMaxHeaderSize());

    assertEquals(HttpClientOptions.DEFAULT_MAX_WAIT_QUEUE_SIZE, options.getMaxWaitQueueSize());
    assertEquals(options, options.setMaxWaitQueueSize(100));
    assertEquals(100, options.getMaxWaitQueueSize());

    Http2Settings initialSettings = randomHttp2Settings();
    assertEquals(new Http2Settings(), options.getInitialSettings());
    assertEquals(options, options.setInitialSettings(initialSettings));
    assertEquals(initialSettings, options.getInitialSettings());

    assertEquals(false, options.isUseAlpn());
    assertEquals(options, options.setUseAlpn(true));
    assertEquals(true, options.isUseAlpn());

    assertNull(options.getSslEngineOptions());
    assertEquals(options, options.setJdkSslEngineOptions(new JdkSSLEngineOptions()));
    assertTrue(options.getSslEngineOptions() instanceof JdkSSLEngineOptions);

    List<HttpVersion> alpnVersions = Collections.singletonList(HttpVersion.HTTP_1_1);
    assertEquals(HttpClientOptions.DEFAULT_ALPN_VERSIONS, options.getAlpnVersions());
    assertEquals(options, options.setAlpnVersions(alpnVersions));
    assertEquals(alpnVersions, options.getAlpnVersions());

    assertEquals(true, options.isHttp2ClearTextUpgrade());
    assertEquals(options, options.setHttp2ClearTextUpgrade(false));
    assertEquals(false, options.isHttp2ClearTextUpgrade());

    assertEquals(null, options.getLocalAddress());


    assertEquals(false,options.isSendUnmaskedFrames());
    assertEquals(options,options.setSendUnmaskedFrames(true));
    assertEquals(true,options.isSendUnmaskedFrames());

    assertEquals(HttpClientOptions.DEFAULT_DECODER_INITIAL_BUFFER_SIZE, options.getDecoderInitialBufferSize());
    assertEquals(options, options.setDecoderInitialBufferSize(256));
    assertEquals(256, options.getDecoderInitialBufferSize());
    assertIllegalArgumentException(() -> options.setDecoderInitialBufferSize(-1));

    assertEquals(HttpClientOptions.DEFAULT_KEEP_ALIVE_TIMEOUT, options.getKeepAliveTimeout());
    assertEquals(options, options.setKeepAliveTimeout(10));
    assertEquals(10, options.getKeepAliveTimeout());
    assertIllegalArgumentException(() -> options.setKeepAliveTimeout(-1));

    assertEquals(HttpClientOptions.DEFAULT_HTTP2_KEEP_ALIVE_TIMEOUT, options.getHttp2KeepAliveTimeout());
    assertEquals(options, options.setHttp2KeepAliveTimeout(10));
    assertEquals(10, options.getHttp2KeepAliveTimeout());
    assertIllegalArgumentException(() -> options.setHttp2KeepAliveTimeout(-1));
  }

  private static final Logger log = LoggerFactory.getLogger(Http1xTest.class);

  @Test
  public void testHttpServerWithIdleTimeoutSendChunkedFile() throws Exception {
    int test = 16;
    int port = DEFAULT_HTTP_PORT;//9993;
    // Does not pass reliably in CI (timeout)
    Assume.assumeFalse(vertx.isNativeTransportEnabled());
    int expected = 16 * 1024 * 1024; // We estimate this will take more than 200ms to transfer with a 1ms pause in chunks
    File sent = TestUtils.tmpFile(".dat", expected);
    //server.close();

    server = vertx
      .createHttpServer(createBaseServerOptions().setPort(port).setIdleTimeout(400).setIdleTimeoutUnit(TimeUnit.MILLISECONDS))
      .requestHandler(
        req -> {
          req.response().sendFile(sent.getAbsolutePath());
        });
    startServer(testAddress);
    log.info("nnnnn => " + test);
    client.request(HttpMethod.GET, testAddress, port, DEFAULT_HTTP_HOST, "/")
      .setHandler(onSuccess(resp -> {
        long now = System.currentTimeMillis();
        int[] length = {0};
        resp.handler(buff -> {
          length[0] += buff.length();
          resp.pause();
          vertx.setTimer(1, id -> {
            resp.resume();
          });
        });
        resp.exceptionHandler(this::fail);
        resp.endHandler(v -> {
          assertEquals(expected, length[0]);
          assertTrue(System.currentTimeMillis() - now > 1000);
          testComplete();
        });
      }))
      .end();
    await();
  }

}
