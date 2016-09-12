import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Flow, Source}

import scala.io.StdIn
import scala.language.postfixOps

object CliProcessor {
  /**
    * Read and process user input from console
    */
  def flow: BidiFlow[Package, Package, Package, Package, NotUsed] = {
    val read = Flow[Package]

    val write = Flow[Package]
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
