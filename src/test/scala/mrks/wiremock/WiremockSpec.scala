package mrks.wiremock

import java.io.IOException

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{MustMatchers, WordSpec}
import play.api.http.{HeaderNames, HttpVerbs, MimeTypes, Status}
import play.api.libs.json.Json
import play.api.libs.ws.{WSAuthScheme, WSClientConfig}
import play.api.libs.ws.ahc.{AhcWSClient, AhcWSClientConfig}
import play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException

class WiremockSpec extends WordSpec
  with MustMatchers
  with WiremockPerTest
  with HeaderNames
  with HttpVerbs
  with MimeTypes
  with Status
  with ScalaFutures {

  implicit val actorSystem  = ActorSystem("wiremock-spec")
  implicit val materializer = ActorMaterializer()(actorSystem)

  override implicit def patienceConfig = PatienceConfig(
    timeout   = scaled(Span(500, Millis)),
    interval  = scaled(Span(20, Millis))
  )

  private val ws = AhcWSClient(AhcWSClientConfig(
    wsClientConfig  = WSClientConfig(followRedirects = false),
    maxRequestRetry = 0,
    keepAlive       = false
  ))

  "Mock" should {
    "return simple status" in {
      mockServer
        .expects(GET, "/users")
        .returns(OK)

      whenReady(ws.url(mockServer.url("/users")).get()) {
        _.status mustBe OK
      }
    }
    "support regex path" in {
      mockServer
        .expects(GET, "/users/[1-9]".r)
        .returns(OK)

      whenReady(ws.url(mockServer.url("/users/1")).get()) {
        _.status mustBe OK
      }
      whenReady(ws.url(mockServer.url("/users/2")).get()) {
        _.status mustBe OK
      }
      whenReady(ws.url(mockServer.url("/users/x")).get()) {
        _.status mustBe NOT_FOUND
      }
    }
    "support headers" in {
      mockServer
        .expects(GET, "/users")
        .withHeaders(ACCEPT -> JSON, AUTHORIZATION -> "Basic.*".r)
        .returns(OK)

      whenReady(ws.url(mockServer.url("/users")).withHttpHeaders(ACCEPT -> JSON).withAuth("u", "p", WSAuthScheme.BASIC).get()) {
        _.status mustBe OK
      }
    }
    "support json body" in {
      mockServer
        .expects(POST, "/users")
        .withBody(Json.obj("name" -> "test"))
        .returns(CREATED)

      whenReady(ws.url(mockServer.url("/users")).post(Json.obj("name" -> "test"))) {
        _.status mustBe CREATED
      }
    }
    "support xml body" in {
      mockServer
        .expects(POST, "/users")
        .withBody(<name>test</name>)
        .returns(CREATED)

      whenReady(ws.url(mockServer.url("/users")).post(<name>test</name>)) {
        _.status mustBe CREATED
      }
    }
    "support source body" in {
      val source = Source(List(ByteString("a"), ByteString("b"), ByteString("c")))

      mockServer
        .expects(POST, "/users")
        .withBody(source)
        .returns(CREATED)

      whenReady(ws.url(mockServer.url("/users")).post(source)) {
        _.status mustBe CREATED
      }
    }
    "return headers" in {
      mockServer
        .expects(GET, "/redirect")
        .returns(SEE_OTHER.withHeaders(LOCATION -> "http://example.com", "X-Custom" -> "Yes"))

      whenReady(ws.url(mockServer.url("/redirect")).get()) { response =>
        response.status mustBe SEE_OTHER
        response.header(LOCATION) must contain ("http://example.com")
        response.header("X-Custom") must contain ("Yes")
      }
    }
    "return json body" in {
      mockServer
        .expects(GET, "/users/1")
        .returns(OK.withBody(Json.obj("id" -> 1)))

      whenReady(ws.url(mockServer.url("/users/1")).get()) { response =>
        response.status mustBe OK
        response.header(CONTENT_TYPE) must contain ("application/json")
        response.body mustBe """{"id":1}"""
      }
    }
    "return xml body" in {
      mockServer
        .expects(GET, "/users/1")
        .returns(OK.withBody(<id>1</id>))

      whenReady(ws.url(mockServer.url("/users/1")).get()) { response =>
        response.status mustBe OK
        response.header(CONTENT_TYPE) must contain ("text/xml")
        response.body mustBe """<id>1</id>"""
      }
    }
    "support server errors" in {
      mockServer
        .expects(GET, "/error-1")
        .fails(ConnectionResetByPeer)

      mockServer
        .expects(GET, "/error-2")
        .fails(EmptyResponse)

      whenReady(ws.url(mockServer.url("/error-1")).get().failed) { error =>
        error mustBe an[IOException]
        error must have message "Connection reset by peer"
      }
      whenReady(ws.url(mockServer.url("/error-2")).get().failed) { error =>
        error mustBe a[RemotelyClosedException]
        error must have message "Remotely closed"
      }
    }
  }
}
