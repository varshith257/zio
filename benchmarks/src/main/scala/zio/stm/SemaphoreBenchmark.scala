package zio.stm

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkUtil._
import zio._

import java.util.concurrent.{Semaphore => JSemaphore}
import java.util.concurrent.TimeUnit
//Benchmarks Semaphore
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 1)
@Measurement(iterations = 4, timeUnit = TimeUnit.SECONDS, time = 1)
@Fork(1)
class SemaphoreBenchmark {

  @Param(Array("1", "10"))
  var nSTM: Int = _

  @Param(Array("1", "2", "5", "10"))
  var fibers: Int = _

  @Param(Array("1", "2", "5"))
  var permits: Int = _

  @Param(Array("4", "10", "20"))
  var threads: Int = _

  def repeat(n: Int)(task: UIO[Any]): UIO[Any] =
    ZIO.foreachParDiscard(1 to n)(_ => task)

  val ops: Int = 1000

  @Benchmark
  def semaphoreContention(): Unit =
    unsafeRun(ZIO.foreachParDiscard(1 to nSTM) { _ =>
      for {
        sem   <- Semaphore.make(math.max(1, fibers / 2L))
        fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeed(1)))))
        _     <- fiber.join
      } yield ()
    })

  @Benchmark
  def tsemaphoreContention(): Unit =
    unsafeRun(ZIO.foreachParDiscard(1 to nSTM) { _ =>
      for {
        sem   <- TSemaphore.make(math.max(1, fibers / 2L)).commit
        fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(ZIO.succeed(1)))))
        _     <- fiber.join
      } yield ()
    })

  // @Benchmark
  // def catsSemaphoreContention(): Unit = {
  //   import cats.effect.std.Semaphore
  //   import cats.effect.unsafe.implicits.global
  //   import cats.effect.{Concurrent, IO => CIO}

  //   (for {
  //     sem   <- Semaphore(fibers / 2L)(Concurrent[CIO])
  //     fiber <- catsForkAll(List.fill(fibers)(catsRepeat(ops)(sem.permit.use(_ => CIO(1)))))
  //     _     <- fiber.join
  //   } yield ()).unsafeRunSync()
  // }

  // ZIO Fair Semaphore Benchmark
  @Benchmark
  def zioFairSemaphoreBenchmark(bh: Blackhole): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(permits.toLong, fair = true)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(Exit.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  // ZIO Unfair Semaphore Benchmark
  @Benchmark
  def zioUnfairSemaphoreBenchmark(bh: Blackhole): Unit =
    unsafeRun(for {
      sem   <- Semaphore.make(permits.toLong, fair = false)
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(Exit.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  // Single Permit Semaphore Benchmark (ZIO)
  @Benchmark
  def singlePermitSemaphoreBenchmark(bh: Blackhole): Unit =
    unsafeRun(for {
      sem <- Semaphore.make(1L, fair = false) // Single permit
      fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops)(sem.withPermit(Exit.succeed(bh.consume(1))))))
      _     <- fiber.join
    } yield ())

  // // Java Fair Semaphore Benchmark
  // @Benchmark
  // def javaSemaphoreFair(bh: Blackhole): Unit =
  //   unsafeRun(for {
  //     lock <- ZIO.succeed(new JSemaphore(permits, true))
  //     fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops) {
  //                ZIO.succeed {
  //                  lock.acquire()
  //                  try bh.consume(1)
  //                  finally lock.release()
  //                }
  //              }))
  //     _ <- fiber.join
  //   } yield ())

  // @Benchmark
  // def javaSemaphoreUnfair(bh: Blackhole): Unit =
  //   unsafeRun(for {
  //     lock <- ZIO.succeed(new JSemaphore(permits, false))
  //     fiber <- ZIO.forkAll(List.fill(fibers)(repeat(ops) {
  //                ZIO.succeed {
  //                  lock.acquire()
  //                  try bh.consume(1)
  //                  finally lock.release()
  //                }
  //              }))
  //     _ <- fiber.join
  //   } yield ())
}
