package totoro.processing

import akka.NotUsed
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge}
import akka.stream.{BidiShape, FlowShape}
import totoro.data.{Dictionary, IrcCommand, Package}
import totoro.{Config, data}

import scala.language.postfixOps

/**
  * Auto answer on PING requests & process commands
  */
object IrcProcessor {
  val systemCommands = List("PING", "376")

  // second argument of IrcCommand contains PRIVMSG message text
  def isCommand(irc: IrcCommand): Boolean =
    systemCommands.contains(irc.command) ||
      ( irc.command == "PRIVMSG" && (irc.args(1).startsWith("~") || irc.args(1).startsWith(Config.Nickname)))

  // a bit of formatting
  def sender(irc: IrcCommand) = irc.source.getOrElse("whoever!").split("!").head
  def message(origin: IrcCommand, text: String) =
    Seq(IrcCommand.PrivMsg(
      if(origin.args.head == Config.Nickname) sender(origin) else origin.args.head,  // answer to PM, if PM got
      sender(origin) + ": " + text))

  def process(pkg: data.Package): data.Package = Package({
    val irc = pkg.content.head
    irc.command match {
      case "PING" => Seq(IrcCommand("PONG", irc.args, None))
      case "376" => Config.Channels.map(IrcCommand.Join) ++ Seq(IrcCommand.Identify(Config.Password))
      case "PRIVMSG" =>
        if (irc.args(1).startsWith(Config.Nickname)) message(irc, Dictionary.Kawaii)
        else {
          val words = irc.args(1).toLowerCase.split("\\s+")
          words(0) match {
            case "~" => message(irc, Dictionary.Kawaii)
            case "~o/" | "~hi" | "~hello" | "~hey" | "~greetings" =>
              message(irc, Dictionary.Hello)
            case "~cookie" => message(irc, Dictionary.Thanks)
            case "~baka" => message(irc, "｡◕_◕｡")
            case "~help" => message(irc, Dictionary.Help)
            case "~orly" | "~?" | "~really?" => message(irc, Dictionary.YesNo)
            case "~music" => message(irc, Dictionary.Music)
            case _ => message(irc, Dictionary.Wtf)
          }
        }
      case _ => message(irc, Dictionary.Wtf)
    }
  })


  def flow: BidiFlow[Package, Package, Package, Package, NotUsed] = {
    BidiFlow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Package](2))
      val merge = builder.add(Merge[Package](2))

      val filterCommands = builder.add(Flow[Package].filter(pkg => isCommand(pkg.content.head)))
      val filterNotCommands = builder.add(Flow[Package].filter(pkg => !isCommand(pkg.content.head)))
      val mapCommands = builder.add(Flow[Package].map(process))

      broadcast.out(0) ~> filterNotCommands
      broadcast.out(1) ~> filterCommands ~> mapCommands ~> merge.in(0)

      BidiShape.fromFlows(FlowShape.of(broadcast.in, filterNotCommands.outlet), FlowShape.of(merge.in(1), merge.out))
    })
  }
}
