package mrks.wiremock

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite, SuiteMixin}

import scala.io.Source

trait WiremockPerTest extends SuiteMixin
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with Wiremock { this: Suite =>

  override val mockServer = MockServer()

  override def beforeAll(): Unit = {
    mockServer.server.start()

    // Ping before first test to initialize server
    mockServer.expects("GET", "/_test/ping").returns(200.withBody("init"))
    val source = Source.fromURL(s"${mockServer.url}/_test/ping")
    source.mkString
    source.close()

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    mockServer.server.stop()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    mockServer.server.resetAll()
    super.beforeEach()
  }
}
