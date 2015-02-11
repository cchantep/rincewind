package rincewind 

import java.io.{ File, FileInputStream }
import java.util.{ EnumSet, Locale }

import javax.servlet.{ DispatcherType, MultipartConfigElement, Servlet }
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import org.eclipse.jetty.server.{ Server ⇒ JettyServer, ServerConnector }
import org.eclipse.jetty.server.handler.{ ContextHandlerCollection }
import org.eclipse.jetty.servlet.{ ServletHolder, ServletContextHandler }
import org.eclipse.jetty.servlets.MultiPartFilter

import scala.concurrent.duration.Duration

import akka.actor.ActorRef

final class WebServer(
    router: Servlet, host: String = "localhost", port: Int = 3000) {

  private val inner = new JettyServer()

  def run(): this.type = {
    Locale.setDefault(Locale.US)

    val handlers = new ContextHandlerCollection()

    // Wrap router
    val holder = new ServletHolder(router)
    holder.setName("Router")
    holder.getRegistration().setMultipartConfig(
      new MultipartConfigElement(System.getProperty("java.io.tmpdir"),
        4194304 /* file size: 4M */ ,
        6291456 /* req size: 6M */ ,
        512 /* always create file attribute */ ))

    // Prepare context
    val ctx = new ServletContextHandler(handlers, "/")

    ctx.addServlet(holder, "/*");

    handlers.addHandler(ctx) // register

    // Prepare connector
    val conn = new ServerConnector(inner)
    conn.setPort(port)
    conn.setHost(host)

    // Run Jetty server
    inner.setHandler(handlers)
    inner.addConnector(conn)
    //inner.setStopAtShutdown(true)

    inner.start()
    println("Server started") // TODO: Logging

    this
  }

  def stop(): this.type = {
    inner.stop()
    this
  }

  def join(): this.type = { inner.join(); this }
}

object WebServer {
  import akka.actor.ActorRef

  def apply(reader: ActorRef) = new WebServer(new WebRouter(reader))
}

object WebExtractor {
  object & {
    def unapply[A](a: A) = Some(a, a)
  }

  object GET {
    def unapply(req: HttpServletRequest) = req.getMethod match {
      case "GET" ⇒ Some(req)
      case _     ⇒ None
    }
  }

  object Path {
    def unapply(req: HttpServletRequest) = Option(req.getRequestURI)
  }
}

trait Responder {
  def writer: resource.ManagedResource[java.io.PrintWriter]

  /** Sets response content type */
  def setContentType(typ: String): Unit

  def setStatus(code: Int): Unit  
}

trait AsyncResponder { self: Responder ⇒
  import scala.util.{ Failure, Try }
  import scala.concurrent.{ Await, ExecutionContext, Future }
  import argonaut._, Argonaut._

  type Complete = () ⇒ Unit

  def defaultTimeout: Duration

  def async(timeout: Long)(f: Complete ⇒ Unit): Unit

  def jsonError(code: Int, msg: String): Try[Unit] = Try {
    setContentType("application/json")
    setStatus(code)
    writer.acquireAndGet(
      _.print(Json("exception" -> jString(msg)).asJson.nospaces))
  }

  def runFuture[U](future: Future[U], timeout: Duration = defaultTimeout)(implicit x: ExecutionContext): Unit = async(timeout.toMillis) { complete ⇒
      Try(Await.result(future, timeout)) recoverWith {
        case i: InterruptedException ⇒
          println(s"Unexpected interruption: $i")
          Failure(i)

        case t: java.util.concurrent.TimeoutException ⇒
          println(s"Server timeout: $t")
          Failure(t)

        case e ⇒
          println(s"Fails to run future: $e")
          jsonError(500, e.getMessage) recover {
            case err ⇒ println(s"Fails to report unexpected error: $e")
          }

      } map (_ ⇒ complete())
  }
}

trait ActorAware {
  def reader: ActorRef
}

trait WebContext // Marker interface

/** Context default factory */
object WebContext {
  import scala.concurrent.ExecutionContext.Implicits.global

  /** Type of default web context */
  sealed trait Default
      extends WebContext with Responder with AsyncResponder with ActorAware

  private trait Servlet extends Default {
    import org.eclipse.jetty.continuation.ContinuationSupport

    def resp: HttpServletResponse
    def req: HttpServletRequest

    def writer = resource.managed(resp.getWriter)
    def setStatus(code: Int) = resp.setStatus(code)

    def setContentType(typ: String) = {
      if (typ.startsWith("text/") || typ == "application/json") {
        resp.setCharacterEncoding("UTF-8")
      }

      resp.setContentType(typ)
    }

    def async(timeout: Long)(f: AsyncResponder#Complete ⇒ Unit) = {
      val continuation = ContinuationSupport.getContinuation(req)

      continuation.setTimeout(timeout)
      continuation.suspend()
      f(() ⇒ continuation.complete())
    }
  }

  def apply(actor: ActorRef, _req: HttpServletRequest, _resp: HttpServletResponse, timeout: Duration = Duration(5, "seconds")): Default = new Servlet {
    val reader = actor
    val resp = _resp
    val req = _req
    val defaultTimeout = timeout
  }
}
