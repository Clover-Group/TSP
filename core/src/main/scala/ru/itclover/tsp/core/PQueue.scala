package ru.itclover.tsp.core
import java.util

import ru.itclover.tsp.core
import ru.itclover.tsp.core.Pattern.Idx

import scala.annotation.tailrec
import scala.collection.convert.ImplicitConversionsToJava._
import scala.language.implicitConversions

trait PQueue[T] {

  def size: Int
  @inline def headOption: Option[IdxValue[T]]
  @inline def dequeue(): (IdxValue[T], PQueue[T])
  @inline def dequeueOption(): Option[(IdxValue[T], PQueue[T])]
  @inline def behead(): PQueue[T]
  @inline def beheadOption(): Option[PQueue[T]]
  @inline def enqueue(idxValues: IdxValue[T]*): PQueue[T]
  @inline def rewindTo(newStart: Idx): PQueue[T]
  @inline def clean(): PQueue[T]
  def drop(i: Long): PQueue[T] = (1L to i).foldLeft(this) { case (x, _) => x.behead() }

  def toSeq: Seq[IdxValue[T]]
}

object PQueue {

  def apply[T](idxValue: IdxValue[T]): PQueue[T] = MutablePQueue(idxValue)

  def empty[T]: PQueue[T] = MutablePQueue(new java.util.ArrayDeque[IdxValue[T]]())

  // PQueue with mutable queue backend. This is the default implementation used in the majority of patterns
  case class MutablePQueue[T](queue: java.util.Deque[IdxValue[T]]) extends PQueue[T] {

    override def headOption: Option[IdxValue[T]] = Option.apply(queue.peekFirst())

    override def dequeue(): (IdxValue[T], PQueue[T]) = {
      val result = queue.remove()
      result -> this
    }
    override def dequeueOption(): Option[(IdxValue[T], PQueue[T])] = {
      if (!queue.isEmpty) {
        Some(queue.poll() -> this)
      } else None
    }
    override def behead(): PQueue[T] = {
      queue.remove()
      this
    }
    override def beheadOption(): Option[PQueue[T]] = if (!queue.isEmpty) {
      queue.remove(); Some(this)
    } else None
    override def clean(): PQueue[T] = MutablePQueue(new java.util.ArrayDeque[IdxValue[T]]())
    override def enqueue(idxValues: IdxValue[T]*): PQueue[T] = {
      idxValues.foreach(queue.offerLast)
      this
    }
    override def toSeq: Seq[IdxValue[T]] = {
      val buffer = scala.collection.mutable.ArrayBuffer.empty[IdxValue[T]]
      import scala.collection.convert.ImplicitConversionsToScala._
      buffer ++= queue.iterator()
      buffer
    }
    override def size: Int = queue.size

    override def rewindTo(newStart: Idx): PQueue[T] = {

      @tailrec
      def inner(q: PQueue[T]): PQueue[T] = {
        headOption match {
          case None                                            => q
          case Some(IdxValue(start, _, _)) if start > newStart => q
          case Some(IdxValue(_, end, _)) if end < newStart     => inner(q.behead())
          case Some(_) => {
            val first = queue.remove()
            queue.offerFirst(first.copy(start = newStart))
            this
          }
        }
      }

      inner(this)
    }
  }

  object MutablePQueue {

    def apply[T](): MutablePQueue[T] = new MutablePQueue(new util.ArrayDeque[IdxValue[T]]())

    def apply[T](idxValue: IdxValue[T]): MutablePQueue[T] = new MutablePQueue({
      val queue: util.ArrayDeque[IdxValue[T]] = new java.util.ArrayDeque();
      queue.offer(idxValue)
      queue
    })
  }

  // Lazy variant of PQueue with func
  case class IdxMapPQueue[A, T](queue: PQueue[A], func: IdxValue[A] => Result[T]) extends PQueue[T] {
    override def size: Int = queue.size
    override def headOption: Option[IdxValue[T]] = queue.headOption.map(x => x.map(_ => func(x)))
    override def dequeue(): (IdxValue[T], PQueue[T]) = {
      val (idx, pqueue) = queue.dequeue()
      (idx.map(_ => func(idx)), IdxMapPQueue(pqueue, func))
    }
    override def dequeueOption(): Option[(IdxValue[T], PQueue[T])] =
      queue.dequeueOption().map { case (idx, pqueue) => (idx.map(_ => func(idx)), IdxMapPQueue(pqueue, func)) }
    override def behead(): PQueue[T] = this.copy(queue = queue.behead())
    override def beheadOption(): Option[PQueue[T]] = queue.beheadOption().map(q => IdxMapPQueue(q, func))
    override def enqueue(idxValues: IdxValue[T]*): PQueue[T] = throw new UnsupportedOperationException(
      "Cannot enqueue to IdxMapPQueue! Bad logic"
    )
    override def clean(): PQueue[T] = this.copy(queue = queue.clean())

    override def toSeq: Seq[IdxValue[T]] = queue.toSeq.map(x => x.map(_ => func(x)))

    override def rewindTo(newStart: Idx): PQueue[T] = this.copy(queue = queue.rewindTo(newStart))
  }

  object MapPQueue {

    def apply[A, T](queue: PQueue[A], func: A => Result[T]): IdxMapPQueue[A, T] = IdxMapPQueue(queue, {
      idx: IdxValue[A] =>
        idx.value.flatMap(func)
    })
  }
}
