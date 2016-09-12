import akka.NotUsed
import akka.stream.{BidiShape, FlowShape}
import akka.stream.scaladsl.{BidiFlow, Broadcast, Flow, GraphDSL, Merge}

import scala.util.Random

object IrcProcessor {
  val random = new Random(System.currentTimeMillis())

  // second argument of IrcCommand contains PRIVMSG message text
  def isCommand(irc: IrcCommand): Boolean =
    irc.command == "PING" || ( irc.command == "PRIVMSG" &&
      (irc.args(1).startsWith("~") || irc.args(1).startsWith(Config.Nickname)))

  def message(to:String, text: String) = IrcCommand(s"PRIVMSG ${Config.Channel}", Seq(to + ": " + text))

  def sender(irc: IrcCommand) = irc.source.getOrElse("whoever!").split("!").head

  def kawaii(): String = random.shuffle(
    List("kawaii!", "nyaa!", "desu!", ":3", ":P", "unicorns freedom!", "nya-a-a...", "^_^")).head
  def wtf(): String = random.shuffle(
    List("i'm sorry, what?", "wtf?", "do you speak english?", "no way!", "wut?!", "baka!")).head
  def hello(): String = random.shuffle(
    List("hi!", "hello!", "i'm glad to see you!", "hey!", "good morning!", ":3", "good to see you!")).head
  def thanks(): String = random.shuffle(
    List("thank you!", "thanks!", "thx!", "cheers!", "thanks a lot!", "i owe you one!", "arigatou!")).head

  def process(irc: IrcCommand): IrcCommand = {
    irc.command match {
      case "PING" => IrcCommand("PONG", irc.args, None)
      case "PRIVMSG" =>
        if (irc.args(1).startsWith(Config.Nickname))
          message(sender(irc), kawaii())
        else irc.args(1).toLowerCase
          match {
            case "~" => message(sender(irc), kawaii())
            case "~hi" | "~hello" | "~hey" | "~greetings" => message(sender(irc), hello())
            case "~cookie" => message(sender(irc), thanks())
            case "~baka" => message(sender(irc), "｡◕_◕｡")
            case _ => message(sender(irc), wtf())
          }
      case _ => message(sender(irc), wtf())
    }
  }

  /**
    * Auto answer on PING requests & commands processing
    */
  def flow: BidiFlow[IrcCommand, IrcCommand, IrcCommand, IrcCommand, NotUsed] = {
    BidiFlow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[IrcCommand](2))
      val merge = builder.add(Merge[IrcCommand](2))

      val filterCommands = builder.add(Flow[IrcCommand].filter(isCommand))
      val filterNotCommands = builder.add(Flow[IrcCommand].filter(!isCommand(_)))
      val mapCommands = builder.add(Flow[IrcCommand].map(process))

      broadcast.out(0) ~> filterNotCommands
      broadcast.out(1) ~> filterCommands ~> mapCommands ~> merge.in(0)

      BidiShape.fromFlows(FlowShape.of(broadcast.in, filterNotCommands.outlet), FlowShape.of(merge.in(1), merge.out))
    })
  }
}
