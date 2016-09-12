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
  def serialization: BidiFlow[ByteString, Package, Package, ByteString, NotUsed] = {
    val read = Flow[ByteString]
      .via(Framing.delimiter(Package.Delimiter, Package.MaxFrameLength))
      .map(Package.read)
      .mapConcat {
      case Success(cmd) => cmd :: Nil
      case Failure(cause) => Nil
    }

    val write = Flow[Package]
      .map(Package.write)
      // initial kick
      .merge(Source.single(ByteString("PING")))
      .map(_ ++ Package.Delimiter)

    BidiFlow.fromFlows(read, write)
  }

  /**
    * Logs everything to console
    */
  def logging: BidiFlow[Package, Package, Package, Package, NotUsed] = {
    def logger(prefix: String) = (pkg: Package) => {
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
    .join(Flow[Package].filter(_ => false))
    .run()
}
