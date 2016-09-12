import akka.NotUsed
import akka.stream.{BidiShape, FlowShape}
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge}

object IrcProcessor {
  val systemCommands = List("PING", "376")

  // second argument of IrcCommand contains PRIVMSG message text
  def isCommand(irc: IrcCommand): Boolean =
    systemCommands.contains(irc.command) ||
      ( irc.command == "PRIVMSG" && (irc.args(1).startsWith("~") || irc.args(1).startsWith(Config.Nickname)))

  // a bit of formatting
  def sender(irc: IrcCommand) = irc.source.getOrElse("whoever!").split("!").head
  def message(origin: IrcCommand, text: String) =
    Seq(IrcCommand.PrivMsg(origin.args.head, sender(origin) + ": " + text))

  def process(pkg: Package): Package = Package({
    val irc = pkg.content.head
    irc.command match {
      case "PING" => Seq(IrcCommand("PONG", irc.args, None))
      case "376" => Config.Channels.map(IrcCommand.Join)
      case "PRIVMSG" =>
        if (irc.args(1).startsWith(Config.Nickname)) message(irc, Dictionary.Kawaii)
        else irc.args(1).toLowerCase
          match {
            case "~" => message(irc, Dictionary.Kawaii)
            case "~o/" | "~hi" | "~hello" | "~hey" | "~greetings" =>
              message(irc, Dictionary.Hello)
            case "~cookie" => message(irc, Dictionary.Thanks)
            case "~baka" => message(irc, "｡◕_◕｡")
            case "~help" => message(irc, Dictionary.Help)
            case _ => message(irc, Dictionary.Wtf)
          }
      case _ => message(irc, Dictionary.Wtf)
    }
  })

  /**
    * Auto answer on PING requests & process commands
    */
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
