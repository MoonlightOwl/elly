import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, BidiShape, FlowShape}
import akka.util.ByteString

import scala.io.StdIn
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
    * Reads/writes IRC commands on all its ends.
    * Logs everything to console
    */
  def logging: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    def logger(prefix: String) = (cmd: IrcCommand) => {
      println(prefix + IrcCommand.write(cmd).utf8String)
      cmd
    }
    BidiFlow.fromFunctions(logger("> "), logger("< "))
  }

  /**
    * Auto answer on PING requests & commands processing
    */
  def ping: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    BidiFlow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[IrcCommand](2))
      val merge = builder.add(Merge[IrcCommand](2))

      val filterPing = builder.add(Flow[IrcCommand].filter(_.command == "PING"))
      val filterNotPing = builder.add(Flow[IrcCommand].filter(_.command != "PING"))
      val mapPingToPong = builder.add(Flow[IrcCommand].map(ping => IrcCommand("PONG", ping.args, None)))

      broadcast.out(0) ~> filterNotPing
      broadcast.out(1) ~> filterPing ~> mapPingToPong ~> merge.in(0)

      BidiShape.fromFlows(FlowShape.of(broadcast.in, filterNotPing.outlet), FlowShape.of(merge.in(1), merge.out))
    })
  }

  /**
    * Read user input from console and process commands from server
    */
  def parsing: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    val read = Flow[IrcCommand]

    val write = Flow[IrcCommand]
      // log in
      .merge(Source(List(
        IrcCommand(s"NICK ${Config.Nickname}", Seq()),
        IrcCommand(s"USER ${Config.Domain} 0 *", Seq(Config.RealName))
      )))
      // read input from console
      .merge((Source fromIterator {
        () => Iterator.continually(
          {
            val data = StdIn.readLine()
            data match {
              case "~join" => IrcCommand(s"JOIN ${Config.Channel}", Seq())
              case "~identify" => IrcCommand("PRIVMSG NickServ", Seq(s"IDENTIFY ${Config.Password}"))

              case message if message.startsWith("~msg") =>
                val Pattern = """(.+)\s(.+)\s(.*)""".r
                message match {
                  case Pattern(prefix, receiver, text) =>
                    IrcCommand("PRIVMSG " + receiver, Seq(text))
                  case _ =>
                    IrcCommand("", Seq())
                }

              case _ => IrcCommand(s"PRIVMSG ${Config.Channel}", Seq(data))
            }
          }
        )
      }).async)

    BidiFlow.fromFlows(read, write)
  }


  Tcp().outgoingConnection(new InetSocketAddress(Config.Server, Config.Port))
    .join(serialization)
    .join(logging)
    .join(ping)
    .join(parsing)
    // combine input with output, while just listening and not saying anything
    .join(Flow[IrcCommand].filter(_ => false))
    .run()
}
