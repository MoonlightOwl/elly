package totoro

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{BidiFlow, Flow, Framing, Source, Tcp}
import akka.util.ByteString
import totoro.data.IrcCommand
import totoro.processing.{CliProcessor, IrcProcessor}

import scala.util.{Failure, Success}

/**
 * Let's talk! Or sit in afk-mode a little ;).
 */
object Elly extends App {
  implicit val system = ActorSystem("actors")
  implicit val materializer = ActorMaterializer()

  /**
    * Reads/writes byte strings from/to the upstream.
    * Writes/reads typed IRC commands to/from the downstream.
    */
  def serialization: BidiFlow[ByteString, data.Package, data.Package, ByteString, NotUsed] = {
    val read = Flow[ByteString]
      .via(Framing.delimiter(data.Package.Delimiter, data.Package.MaxFrameLength))
      .map(data.Package.read)
      .mapConcat {
      case Success(cmd) => cmd :: Nil
      case Failure(cause) => Nil
    }

    val write = Flow[data.Package]
      .map(data.Package.write)
      // initial kick
      .merge(Source.single(ByteString("PING")))
      .map(_ ++ data.Package.Delimiter)

    BidiFlow.fromFlows(read, write)
  }

  /**
    * Logs everything to console
    */
  def logging: BidiFlow[data.Package, data.Package, data.Package, data.Package, NotUsed] = {
    def logger(prefix: String) = (pkg: data.Package) => {
      pkg.content.foreach(irc => println(prefix + IrcCommand.write(irc).utf8String))
      pkg
    }
    BidiFlow.fromFunctions(logger("> "), logger("< "))
  }


  Tcp().outgoingConnection(new InetSocketAddress(Config.Server, Config.Port))
    .join(serialization)
    .join(logging)
    .join(IrcProcessor.flow)
    .join(CliProcessor.flow)
    // combine input with output, while just listening and not saying anything
    .join(Flow[data.Package].filter(_ => false))
    .run()
}
