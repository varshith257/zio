import zio._
import zio.test._
import zio.test.Assertion._
import zio.Console
import java.util.concurrent.atomic.AtomicBoolean

object SignalHandlingSpec extends ZIOSpecDefault {

  def spec = suite("SignalHandlingSpec")(
    test("handles SIGINT and interrupts fibers") {
      for {
        interrupted <- Ref.make(false)
        latch       <- Promise.make[Nothing, Unit]
        fiber <- (latch.succeed(()) *> Console
                   .printLine("Hello, fiber!")
                   .schedule(Schedule.spaced(1.second)))
                   .onInterrupt(interrupted.set(true))
                   .fork
        _ <- latch.await
        _ <- ZIO.succeed {
               // Simulate SIGINT by calling the signal handler
               Signal.handle("INT", _ => fiber.interrupt.unit)
             }
        _      <- fiber.join.ignore
        result <- interrupted.get
      } yield assert(result)(isTrue)
    }
  )
}
