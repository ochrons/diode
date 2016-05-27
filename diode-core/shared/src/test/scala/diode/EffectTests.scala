package diode

import java.util.concurrent.atomic.AtomicInteger

import utest._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

object EffectTests extends TestSuite {
  def tests = TestSuite {
    case object A extends Action
    case object B extends Action
    case object C extends Action
    def efA = Effect(Future(A))
    def efB = Effect(Future(B))
    def efC = Effect(Future(C))

    'Effect - {
      'run - {
        var x: Action = B
        efA.run( y => x = y ).map { _ =>
          assert(x == A)
        }
      }
      'toFuture - {
        efA.toFuture.map { z =>
          assert(z == List(A))
        }
      }
      'map - {
        efA.map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(A, A))))
        }
      }
      'flatMap - {
        efA.flatMap(x => Future(ActionSeq(List(x, x)))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(A, A))))
        }
      }
      'after - {
        import diode.Implicits._
        val now = System.currentTimeMillis()
        efA.after(100.milliseconds).map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(A, A))))
          assert(System.currentTimeMillis() - now > 80)
        }
      }
      '+ - {
        val e = efA + efB
        val ai = new AtomicInteger(0)
        e.run(x => ai.incrementAndGet()).map { _ =>
          assert(ai.intValue() == 2)
        }
      }
      '>> - {
        val e = efA >> efB >> efC
        var r = List.empty[Action]
        e.run(x => r = r :+ x ).map { _ =>
          assert(r == List(A, B, C))
        }
      }
      '<< - {
        val e = efA << efB << efC
        var r = List.empty[Action]
        e.run(x => r = r :+ x ).map { _ =>
          assert(r == List(C, B, A))
        }
      }
    }
    'EffectSeq - {
      'map - {
        val e = efA >> efB >> efC
        assert(e.size == 3)
        e.map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(C, C))))
        }
      }
      'flatMap - {
        val e = efA >> efB >> efC
        assert(e.size == 3)
        e.flatMap(x => Future(ActionSeq(List(x, x)))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(C, C))))
        }
      }
      'complex - {
        val e = (efA + efB) >> efC
        assert(e.size == 3)
        e.map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z == List(ActionSeq(List(C, C))))
        }

      }
    }
    'EffectSet - {
      'map - {
        val e = efA + efB + efC
        assert(e.size == 3)
        e.map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z.toSet == Set(ActionSeq(List(A, A)), ActionSeq(List(B, B)), ActionSeq(List(C, C))))
        }
      }
      'flatMap - {
        val e = efA + efB + efC
        assert(e.size == 3)
        e.flatMap(x => Future(ActionSeq(List(x, x)))).toFuture.map { z =>
          assert(z.toSet == Set(ActionSeq(List(A, A)), ActionSeq(List(B, B)), ActionSeq(List(C, C))))
        }
      }
      'complex - {
        val e = (efA >> efB) + efC
        assert(e.size == 3)
        e.map(x => ActionSeq(List(x, x))).toFuture.map { z =>
          assert(z.toSet == Set(ActionSeq(List(B, B)), ActionSeq(List(C, C))))
        }
      }
    }
  }
}
