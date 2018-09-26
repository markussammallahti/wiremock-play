package mrks.wiremock

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, ResponseDefinitionBuilder, WireMock}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.{Fault => WmFault}
import play.api.http.HeaderNames
import play.api.libs.ws.{BodyWritable, InMemoryBody, SourceBody}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.util.matching.Regex

trait Wiremock {
  implicit def materializer: Materializer
  val mockServer: MockServer

  sealed trait Fault {
    def value: WmFault
  }
  case object ConnectionResetByPeer extends Fault {
    val value = WmFault.CONNECTION_RESET_BY_PEER
  }
  case object EmptyResponse extends Fault {
    val value = WmFault.EMPTY_RESPONSE
  }
  case object MalformedResponseChunk extends Fault {
    val value = WmFault.MALFORMED_RESPONSE_CHUNK
  }
  case object RandomDataThenClose extends Fault {
    val value = WmFault.RANDOM_DATA_THEN_CLOSE
  }

  private def resolveBody[A: BodyWritable](body: A) = {
    val writable = implicitly[BodyWritable[A]]

    writable.transform(body) match {
      case InMemoryBody(bytes) =>
        (Some(bytes.utf8String), Some(writable.contentType))

      case SourceBody(source) =>
        val bytes = Await.result(source.runReduce(_ ++ _), Duration.Inf)
        (Some(bytes.utf8String), Some(writable.contentType))

      case _ =>
        (None, None)
    }
  }

  private[wiremock] class MockBuilder(server: WireMockServer, b: MappingBuilder) {
    def withBody[A: BodyWritable](body: A): MockBuilder = {
      val (content, ct) = resolveBody(body)
      content.foreach(v => b.withRequestBody(WireMock.equalTo(v)))
      ct.foreach(v => b.withHeader(HeaderNames.CONTENT_TYPE, WireMock.equalTo(v)))
      this
    }

    def withHeaders(headers: Map[String,Any]): MockBuilder = {
      headers.foreach {
        case (key, value: Regex) =>
          b.withHeader(key, WireMock.matching(value.regex))

        case (key, value) =>
          b.withHeader(key, WireMock.equalTo(value.toString))
      }
      this
    }

    def withHeaders(headers: (String,Any)*): MockBuilder = {
      this.withHeaders(headers.toMap)
    }

    def returns(response: ResponseBuilder): Unit = {
      server.stubFor(b.willReturn(response.builder))
    }

    def fails(fault: Fault): Unit = {
      server.stubFor(b.willReturn(WireMock.aResponse().withFault(fault.value)))
    }
  }

  implicit def intToResponseBuilder(status: Int): ResponseBuilder = new ResponseBuilder(WireMock.aResponse().withStatus(status))

  private[wiremock] class ResponseBuilder(b: ResponseDefinitionBuilder) {
    def withBody[A: BodyWritable](body: A): ResponseBuilder = {
      val (content, ct) = resolveBody(body)
      content.foreach(b.withBody)
      ct.foreach(b.withHeader(HeaderNames.CONTENT_TYPE, _))
      this
    }

    def withHeaders(headers: Map[String,String]): ResponseBuilder = {
      headers.foreach { case (key, value) =>
        b.withHeader(key, value)
      }
      this
    }

    def withHeaders(headers: (String,String)*): ResponseBuilder = {
      this.withHeaders(headers.toMap)
    }

    private[wiremock] def builder = b
  }

  private[wiremock] case class MockServer(server: WireMockServer) {
    def url: String = server.url("").dropRight(1)
    def url(path: String): String = server.url(path)

    def expects(method: String, path: String): MockBuilder = new MockBuilder(server, WireMock.request(method, WireMock.urlEqualTo(path)))
    def expects(method: String, path: Regex): MockBuilder = new MockBuilder(server, WireMock.request(method, WireMock.urlMatching(path.regex)))
  }

  object MockServer {
    def apply(port: Int): MockServer = MockServer(new WireMockServer(port))
    def apply(): MockServer = MockServer(new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()))
  }
}
