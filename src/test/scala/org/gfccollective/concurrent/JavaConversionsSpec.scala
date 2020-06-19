package org.gfccollective.concurrent

import java.util.concurrent.{ExecutorService => JExecutorService, ScheduledExecutorService => JScheduledExecutorService}

import org.gfccollective.concurrent.{ScheduledExecutorService => GScheduledExecutorService}

import org.scalatestplus.mockito.{MockitoSugar => ScalaTestMockitoSugar}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class JavaConversionsSpec extends AnyWordSpecLike
  with Matchers
  with MockitoSugar {

  "When converting java `ScheduledExecutorService`, and JavaConversions is imported" must {
    "compile" in {

      import JavaConversions._

      val mockJavaSchExecService = mock[JScheduledExecutorService]

      val serviceUnderTest: GScheduledExecutorService = mockJavaSchExecService
    }
  }

  "When converting java `ExecutorService`, and JavaConversions is imported" must {
    "compile" in {

      import JavaConversions._

      val mockJavaService = mock[JExecutorService]

      val serviceUnderTest: ExecutorService = mockJavaService
    }
  }
}
