package com.ibm.spark.kernel.protocol.v5.interpreter

import java.io.OutputStream

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.ibm.spark.interpreter.Interpreter
import com.ibm.spark.kernel.protocol.v5.interpreter.tasks._
import com.ibm.spark.kernel.protocol.v5.content._
import com.ibm.spark.interpreter._

import scala.concurrent.duration._

object InterpreterActor {
  def props(interpreter: Interpreter): Props =
    Props(classOf[InterpreterActor], interpreter)
}

// TODO: Investigate restart sequence
//
// http://doc.akka.io/docs/akka/2.2.3/general/supervision.html
//
// "create new actor instance by invoking the originally provided factory again"
//
// Does this mean that the interpreter instance is not gc and is passed in?
//
class InterpreterActor(
  interpreterTaskFactory: InterpreterTaskFactory
) extends Actor {
  // NOTE: Required to provide the execution context for futures with akka
  import context._

  // NOTE: Required for ask (?) to function... maybe can define elsewhere?
  implicit val timeout = Timeout(100000.days)

  //
  // List of child actors that the interpreter contains
  //
  private var executeRequestTask: ActorRef = _

  /**
   * Initializes all child actors performing tasks for the interpreter.
   */
  override def preStart = {
    executeRequestTask = interpreterTaskFactory.ExecuteRequestTask(
      context, InterpreterChildActorType.ExecuteRequestTask.toString)
  }

  override def receive: Receive = {
    case (executeRequest: ExecuteRequest, outputStream: OutputStream) =>
      val data = (executeRequest, outputStream)
      (executeRequestTask ? data) recover {
        case ex: Throwable =>
          Right(ExecuteError(
            ex.getClass.getName,
            ex.getLocalizedMessage,
            ex.getStackTrace.map(_.toString).toList)
          )
      } pipeTo sender
  }
}
