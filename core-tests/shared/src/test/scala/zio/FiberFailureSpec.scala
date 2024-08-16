package zio

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect._
import java.io.{ByteArrayOutputStream, PrintStream}

object FiberFailureSpec extends ZIOBaseSpec {

  val expectedStackTrace = Seq(
    "FiberFailure",
    "apply",
    "getOrThrowFiberFailure",
    "runLoop"
  )

  def spec = suite("FiberFailureSpec")(
    test("FiberFailure getStackTrace includes relevant ZIO stack traces") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }

      val stackTrace = ZIO
        .attempt(subcall())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTraceStr = fiberFailure.getStackTrace.map(_.toString).mkString("\n")
            ZIO.succeed(stackTraceStr)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      stackTrace.flatMap { trace =>
        ZIO.succeed {
          assertTrue(expectedStackTrace.forall(element => trace.contains(element)))
        }
      }
    },
    test("FiberFailure toString should match cause.prettyPrint") {
      val cause        = Cause.fail(new Exception("Test Exception"))
      val fiberFailure = FiberFailure(cause)

      assert(fiberFailure.toString)(equalTo(cause.prettyPrint))
    },
    test("FiberFailure printStackTrace should correctly output the stack trace") {
      val cause        = Cause.fail(new Exception("Test Exception"))
      val fiberFailure = FiberFailure(cause)

      val outputStream = new ByteArrayOutputStream()
      val printStream  = new PrintStream(outputStream)

      fiberFailure.printStackTrace(printStream)

      val stackTraceOutput = new String(outputStream.toByteArray)

      assertTrue(
        stackTraceOutput.contains("FiberFailure"),
        stackTraceOutput.contains("Test Exception")
      )
    },
    test("FiberFailure captures the stack trace for ZIO.fail with String") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.log(s"Captured Stack Trace:\n$stackTrace") *>
              ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              expectedStackTrace.forall(element => stackTrace.contains(element))
          )
        }
      }
    },
    test("FiberFailure captures the stack trace for ZIO.fail with Throwable") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail(new Exception("boom"))).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              expectedStackTrace.forall(element => stackTrace.contains(element))
          )
        }
      }
    },
    test("FiberFailure captures the stack trace for ZIO.die") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.die(new RuntimeException("boom"))).getOrThrowFiberFailure()
        }
      def call1(): Unit = subcall()

      val fiberFailureTest = ZIO
        .attempt(call1())
        .catchAll {
          case fiberFailure: FiberFailure =>
            val stackTrace = fiberFailure.getStackTrace.mkString("\n")
            ZIO.succeed(stackTrace)
          case other =>
            ZIO.succeed(s"Unexpected failure: ${other.getMessage}")
        }
        .asInstanceOf[ZIO[Any, Nothing, String]]

      fiberFailureTest.flatMap { stackTrace =>
        ZIO.succeed {
          assertTrue(
            stackTrace.contains("call1") &&
              stackTrace.contains("subcall") &&
              expectedStackTrace.forall(element => stackTrace.contains(element))
          )
        }
      }
    },
    test("FiberFailure stack traces should be consistent across getStackTrace, toString, and printStackTrace") {
      def subcall(): Unit =
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ZIO.fail("boom")).getOrThrowFiberFailure()
        }

      val result = ZIO
        .attempt(subcall())
        .catchAll {
          case fiberFailure: FiberFailure =>
            // Capture the output of getStackTrace
            val stackTraceFromMethod = fiberFailure.getStackTrace.map(_.toString).mkString("\n")

            // Capture the output of toString
            val toStringOutput = fiberFailure.toString

            // Capture the output of printStackTrace
            val outputStream = new ByteArrayOutputStream()
            val printStream  = new PrintStream(outputStream)
            fiberFailure.printStackTrace(printStream)
            val stackTraceFromPrint = new String(outputStream.toByteArray)

            // Logging for review
            ZIO.log(s"Captured Stack Trace from getStackTrace:\n$stackTraceFromMethod") *>
              ZIO.log(s"Captured toString Output:\n$toStringOutput") *>
              ZIO.log(s"Captured Stack Trace from printStackTrace:\n$stackTraceFromPrint") *>
              ZIO.succeed((stackTraceFromMethod, toStringOutput, stackTraceFromPrint))
          case other =>
            ZIO.fail(new RuntimeException(s"Unexpected failure: ${other.getMessage}"))
        }
        .asInstanceOf[ZIO[Any, Nothing, (String, String, String)]]

      result.flatMap { case (stackTraceFromMethod, toStringOutput, stackTraceFromPrint) =>
        ZIO.succeed {
          assertTrue(
            toStringOutput.contains(stackTraceFromMethod),
            stackTraceFromPrint.contains(stackTraceFromMethod),
            toStringOutput == stackTraceFromPrint
          )
        }
      }
    }
  ) @@ exceptJS
}
