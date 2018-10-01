package org.basex.http.ws;

import static org.basex.http.web.WebText.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import javax.servlet.http.*;

import org.basex.core.*;
import org.basex.http.*;
import org.basex.http.web.*;
import org.basex.query.ann.*;
import org.basex.query.value.*;
import org.basex.server.*;
import org.basex.server.Log.*;
import org.basex.util.*;
import org.eclipse.jetty.websocket.api.*;

/**
 * This class defines an abstract WebSocket. It inherits the Jetty WebSocket adapter.
 *
 * @author BaseX Team 2005-18, BSD License
 * @author Johannes Finckh
 */
public final class WebSocket extends WebSocketAdapter implements ClientInfo {
  /** WebSocket attributes. */
  public final ConcurrentHashMap<String, Value> atts = new ConcurrentHashMap<>();
  /** Database context. */
  public final Context context;
  /** Path. */
  public final WsPath path;

  /** Header parameters. */
  final Map<String, String> headers = new HashMap<>();
  /** Servlet request. */
  final HttpServletRequest req;

  /** Client WebSocket id. */
  public String id;
  /** HTTP Session. */
  public HttpSession session;

  /**
   * Constructor.
   * @param req request
   */
  WebSocket(final HttpServletRequest req) {
    this.req = req;

    final String pi = req.getPathInfo();
    this.path = new WsPath(pi != null ? pi : "/");
    session = req.getSession();

    final Context ctx = HTTPContext.context();
    context = new Context(ctx, this);
    context.user(ctx.user());
  }

  /**
   * Creates a new WebSocket instance.
   * @param req request
   * @return WebSocket or {@code null}
   */
  static WebSocket get(final HttpServletRequest req) {
    final WebSocket ws = new WebSocket(req);
    try {
      if(!WebModules.get(ws.context).findWs(ws, null).isEmpty()) return ws;
    } catch(final Exception ex) {
      Util.debug(ex);
      throw new CloseException(StatusCode.ABNORMAL, ex.getMessage());
    }
    return null;
  }

  @Override
  public void onWebSocketConnect(final Session sess) {
    super.onWebSocketConnect(sess);
    id = WsPool.get().add(this);

    run("[WS-OPEN] " + req.getRequestURL(), null, () -> {
      // add headers (for binding them to the XQuery parameters in the corresponding bind method)
      final UpgradeRequest ur = sess.getUpgradeRequest();
      final BiConsumer<String, String> addHeader = (k, v) -> {
        if(v != null) headers.put(k, v);
      };

      addHeader.accept("Http-Version", ur.getHttpVersion());
      addHeader.accept("Origin", ur.getOrigin());
      addHeader.accept("Protocol-version", ur.getProtocolVersion());
      addHeader.accept("QueryString", ur.getQueryString());
      addHeader.accept("IsSecure", String.valueOf(ur.isSecure()));
      addHeader.accept("RequestURI", ur.getRequestURI().toString());

      final String[] names = { "Host", "Sec-WebSocket-Version" };
      for(final String name : names) addHeader.accept(name, ur.getHeader(name));

      findAndProcess(Annotation._WS_CONNECT, null);
    });
  }

  @Override
  public void onWebSocketError(final Throwable cause) {
    run("[WS-ERROR] " + req.getRequestURL() + ": " + cause.getMessage(), null, () -> {
      findAndProcess(Annotation._WS_ERROR, cause.getMessage());
    });
  }

  @Override
  public void onWebSocketClose(final int status, final String message) {
    try {
      run("[WS-CLOSE] " + req.getRequestURL(), status, () -> {
        findAndProcess(Annotation._WS_CLOSE, null);
      });
    } finally {
      WsPool.get().remove(id);
      super.onWebSocketClose(status, message);
    }
  }

  @Override
  public void onWebSocketText(final String message) {
    findAndProcess(Annotation._WS_MESSAGE, message);
  }

  @Override
  public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {
    findAndProcess(Annotation._WS_MESSAGE, payload);
  }

  @Override
  public String clientAddress() {
    final Session ws = getSession();
    return ws != null ? ws.getRemoteAddress().toString() : null;
  }

  @Override
  public String clientName() {
    Object obj = atts.get(ID);
    if(obj == null && session != null) obj = session.getAttribute(ID);
    final byte[] value = HTTPContext.token(obj);
    return value != null ? Token.string(value) : context.user().name();
  }

  /**
   * Closes the WebSocket connection.
   */
  public void close() {
    WsPool.get().remove(id);
    getSession().close();
  }

  /**
   * Finds a function and processes it.
   * @param ann annotation
   * @param message message (can be {@code null}; otherwise string or byte array)
   */
  private void findAndProcess(final Annotation ann, final Object message) {
    // check if an HTTP session exists, and if it still valid
    try {
      if(session != null) session.getCreationTime();
    } catch(final IllegalStateException ex) {
      session = null;
    }

    try {
      // find function to evaluate
      final WsFunction func = WebModules.get(context).websocket(this, ann);
      if(func != null) new WsResponse(this).create(func, message);
    } catch(final Exception ex) {
      Util.debug(ex);
      try {
        // in the case of an error, inform the client about it.
        // workflow is analogous to QueryException handling in BaseXServlet class
        getRemote().sendString(ex.getMessage());
      } catch(final IOException e) {
        Util.debug(e);
      }
      throw ex instanceof RuntimeException ? (RuntimeException) ex :
        new CloseException(StatusCode.ABNORMAL, ex);
    }
  }

  /**
   * Runs a function and creates log output.
   * @param info log string
   * @param status close status
   * @param func function to be run
   */
  private void run(final String info, final Integer status, final Runnable func) {
    context.log.write(LogType.REQUEST, info, null, context);
    final Performance perf = new Performance();
    try {
      func.run();
    } catch (final Exception ex) {
      context.log.write(LogType.ERROR, "", perf, context);
      throw ex;
    }
    context.log.write((status != null ? status : LogType.OK).toString(), "", perf, context);
  }
}
