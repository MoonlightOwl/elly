import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.util._


object Elly extends App {
  implicit val system = ActorSystem("actors")
  implicit val materializer = ActorMaterializer()

  /**
    * Reads/writes byte strings from/to the upstream.
    * Writes/reads typed IRC commands to/from the downstream.
    */
  def serialization: BidiFlow[ByteString, IrcCommand, IrcCommand, ByteString, NotUsed] = {
    val read = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\r\n"), 65536))
      .map(IrcCommand.read)
      .mapConcat {
      case Success(cmd) => cmd :: Nil
      case Failure(cause) => Nil
    }

    val write = Flow[IrcCommand]
      .map(IrcCommand.write)
      // initial kick
      .merge(Source.single(ByteString("PING")))
      .map(_ ++ ByteString("\r\n"))

    BidiFlow.fromFlows(read, write)
  }

  /**
    * Logs everything to console
    */
  def logging: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    def logger(prefix: String) = (cmd: IrcCommand) => {
      println(prefix + IrcCommand.write(cmd).utf8String)
      cmd
    }
    BidiFlow.fromFunctions(logger("> "), logger("< "))
  }


  Tcp().outgoingConnection(new InetSocketAddress(Config.Server, Config.Port))
    .join(serialization)
    .join(logging)
    .join(IrcProcessor.flow)
    .join(CliProcessor.flow)
    // combine input with output, while just listening and not saying anything
    .join(Flow[IrcCommand].filter(_ => false))
    .run()
}
