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

import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.*;
import io.vertx.core.streams.Pump;
import io.vertx.test.core.Repeat;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakestream.FakeStream;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.*;
import static java.util.Collections.singletonList;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpTest extends HttpTestBase {

  static final Logger log = LoggerFactory.getLogger(HttpTest.class);

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  protected File testDir;
  private File tmp;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testDir = testFolder.newFolder();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", USE_NATIVE_TRANSPORT);
      tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    }
  }

  @Test
  public abstract void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd() throws Exception;

  protected void testCloseHandlerNotCalledWhenConnectionClosedAfterEnd(int expected) throws Exception {
    AtomicInteger closeCount = new AtomicInteger();
    AtomicInteger endCount = new AtomicInteger();
    server.requestHandler(req -> {
      req.response().closeHandler(v -> {
        closeCount.incrementAndGet();
      });
      req.response().endHandler(v -> {
        endCount.incrementAndGet();
      });
      req.connection().closeHandler(v -> {
        assertEquals(expected, closeCount.get());
        assertEquals(1, endCount.get());
        testComplete();
      });
      req.response().end("some-data");
    });
    startServer(testAddress);
    client.request(HttpMethod.GET, testAddress, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/somepath")
      .setHandler(onSuccess(resp -> {
        resp.endHandler(v -> {
          resp.request().connection().close();
        });
      }))
      .end();
    await();
  }
}
