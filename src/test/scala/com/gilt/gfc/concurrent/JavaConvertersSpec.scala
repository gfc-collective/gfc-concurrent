package com.gilt.gfc.concurrent

import java.util.concurrent.{ExecutorService => JExecutorService, ScheduledExecutorService => JScheduledExecutorService}

import org.scalatest.mockito.{MockitoSugar => ScalaTestMockitoSugar}
import org.scalatest.{Matchers => ScalaTestMatchers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class JavaConvertersSpec extends AnyWordSpecLike
  with Matchers
  with MockitoSugar {

  "When converting java `ScheduledExecutorService`, and JavaConverters is imported" must {
    "compile" in {

      import JavaConverters._

      val mockJavaSchExecService = mock[JScheduledExecutorService]

      val serviceUnderTest: AsyncScheduledExecutorService = mockJavaSchExecService.asScala
    }
  }

  "When converting java `ExecutorService`, and JavaConverters is imported" must {
    "compile" in {

      import JavaConverters._

      val mockJavaService = mock[JExecutorService]

      val serviceUnderTest: ExecutorService = mockJavaService.asScala
    }
  }
}
