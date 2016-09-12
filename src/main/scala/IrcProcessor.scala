import akka.NotUsed
import akka.stream.{BidiShape, FlowShape}
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge}

import scala.util.Random

object IrcProcessor {
  val random = new Random(System.currentTimeMillis())

  val systemCommands = List("PING", "376")

  // second argument of IrcCommand contains PRIVMSG message text
  def isCommand(irc: IrcCommand): Boolean =
    systemCommands.contains(irc.command) ||
      ( irc.command == "PRIVMSG" && (irc.args(1).startsWith("~") || irc.args(1).startsWith(Config.Nickname)))

  def message(channel: String, to:String, text: String) = IrcCommand.PrivMsg(channel, to + ": " + text)

  def sender(irc: IrcCommand) = irc.source.getOrElse("whoever!").split("!").head

  def kawaii(): String = random.shuffle(
    List("kawaii!", "nyaa!", "desu!", ":3", ":P", "unicorns freedom!", "nya-a-a...", "^_^")).head
  def wtf(): String = random.shuffle(
    List("i'm sorry, what?", "wtf?", "do you speak english?", "no way!", "wut?!",
      "baka!", ".-.", "what language is this?")).head
  def hello(): String = random.shuffle(
    List("o/", "hi!", "hello!", "i'm glad to see you!", "hey!", "good morning!", ":3", "good to see ya!")).head
  def thanks(): String = random.shuffle(
    List("thank you!", "thanks!", "thx!", "cheers!", "thanks a lot!", "i owe you one!", "arigatou!")).head

  def process(pkg: Package): Package = Package({
    val irc = pkg.content.head
    irc.command match {
      case "PING" => Seq(IrcCommand("PONG", irc.args, None))
      case "376" => Config.Channels.map(IrcCommand.Join)
      case "PRIVMSG" =>
        if (irc.args(1).startsWith(Config.Nickname))
          Seq(message(irc.args.head, sender(irc), kawaii()))
        else irc.args(1).toLowerCase
          match {
            case "~" => Seq(message(irc.args.head, sender(irc), kawaii()))
            case "~o/" | "~hi" | "~hello" | "~hey" | "~greetings" => Seq(message(irc.args.head, sender(irc), hello()))
            case "~cookie" => Seq(message(irc.args.head, sender(irc), thanks()))
            case "~baka" => Seq(message(irc.args.head, sender(irc), "｡◕_◕｡"))
            case _ => Seq(message(irc.args.head, sender(irc), wtf()))
          }
      case _ => Seq(message(irc.args.head, sender(irc), wtf()))
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
