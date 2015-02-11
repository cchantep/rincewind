package rincewind

import javax.servlet.{
  Servlet,
  ServletConfig,
  ServletRequest,
  ServletResponse
}
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }

import akka.actor.ActorRef

import rincewind.WebExtractor._

final class WebRouter(reader: ActorRef)
    extends Servlet {

  var config: ServletConfig = null
  def init(c: ServletConfig) { config = c }
  override def getServletConfig = config

  def destroy() {}
  override def getServletInfo = "WebRouter"

  override def service(req: ServletRequest, resp: ServletResponse) {
    val hreq = req.asInstanceOf[HttpServletRequest]
    val hresp = resp.asInstanceOf[HttpServletResponse]
    val ctx = WebContext(reader, hreq, hresp)

    try {
      hreq match {
        case GET(Path("/random")) ⇒ WebController.random(ctx)

        case _ ⇒
          println(s"Route not found: ${hreq.getMethod} ${hreq.getRequestURI}")
          hresp.sendError(HttpServletResponse.SC_BAD_REQUEST)
      }
    } catch {
      case e: Throwable ⇒
        e.printStackTrace() // TODO: Logging
        hresp.sendError(HttpServletResponse.SC_BAD_REQUEST)
    }
  }
}

object WebController {
  import scala.concurrent.Promise
  import scala.concurrent.ExecutionContext.Implicits.global

  import argonaut._, Argonaut._
  import argonaut.Argonaut.{ jString, jObjectAssocList }

  /** Requirements for first kind of context */
  type Ctx1 = WebContext with Responder with AsyncResponder with ActorAware

  def random(ctx: Ctx1): Unit = ctx.runFuture {
    ctx.setContentType("application/json")

    val promise = Promise[(String, List[Int])]()

    ctx.reader ! PickRandomSeq(promise)

    promise.future map {
      case (uuid, data) =>
        ctx.writer.acquireAndGet(_.println(jObjectAssocList(List(
          "uuid" -> jString(uuid), "sequence" -> data.asJson)).nospaces))
    }
  }
}
