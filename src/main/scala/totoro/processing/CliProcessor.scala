package totoro.processing

import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Flow, Source}
import totoro.data.{IrcCommand, Package}
import totoro.{Config, data}

import scala.io.StdIn
import scala.language.postfixOps

/**
  * Read and process user input from console
  */
object CliProcessor {

  def flow: BidiFlow[data.Package, data.Package, data.Package, data.Package, NotUsed] = {
    val read = Flow[data.Package]

    val write = Flow[data.Package]
      // log in
      .merge(Source single Package(Seq(
        IrcCommand.Nick(Config.Nickname),
        IrcCommand.User(Config.Domain, Config.RealName)))
      )
      // read input from console
      .merge(Source fromIterator { () => Iterator.continually(
        Package(Seq({
          val data = StdIn.readLine()
          data match {
            case "~join" => IrcCommand.Join(Config.Channels.head)
            case "~identify" => IrcCommand.Identify(Config.Password)

            case message if message.startsWith("~msg") =>
              val Pattern = """(.+)\s(.+)\s(.*)""".r
              message match {
                case Pattern(prefix, receiver, text) => IrcCommand.PrivMsg(receiver, text)
                case _ => IrcCommand.Null()
              }

            case _ => IrcCommand.PrivMsg(Config.Channels.head, data)
          }
        }))
      )} async)

    BidiFlow.fromFlows(read, write)
  }
}
