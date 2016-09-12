import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Flow, Source}

import scala.io.StdIn

object CliProcessor {
  /**
    * Read and process user input from console
    */
  def flow: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
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
}
